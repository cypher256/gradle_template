package jp.example;

import static java.lang.String.*;
import static java.util.Arrays.*;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import javax.servlet.DispatcherType;
import javax.servlet.FilterChain;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.lang3.exception.ExceptionUtils;

import jp.co.future.uroborosql.SqlAgent;
import jp.co.future.uroborosql.UroboroSQL;
import jp.co.future.uroborosql.config.SqlConfig;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * Servlet JSP 単一レイヤーアーキテクチャーコントローラーのサンプルです。<br>
 * サンプルは、以下のポリシーに基づいています。
 * <ul>
 * <li>できるだけ純粋な Servlet API と JSP JSTL。DB アクセスは簡単操作が可能な uroboroSQL。
 * <li>シンプルな単一レイヤー: 分散開発や分散実行が不要なプロジェクト向けです。
 * <li>トランザクション操作不要: DB コネクションの取得・解放やロールバックは、このクラスが行います。
 * <li>入力チェックなどのエラー: IllegalArgumentException をスローすれば、message 属性にセットされます。
 * <li>自動 CSRF チェック: Cookie ベースのため、JSP や各サーブレット側では二重送信や CSRF の考慮は不要です。
 * </ul>
 * @author Generated by Pleiades All in One New Gradle Project Wizard (EPL)
 */
@WebFilter("/*")
@Slf4j
public class SingleTierController extends HttpFilter {
	
	/**
	 * @return カレントスレッドの DAO インスタンス
	 * <pre>
	 * 自動採番の主キーを持つテーブは、id などのエンティティに関するアノテーションは不要です。
	 * スネークケース、キャメルケースは自動変換されます。ただし、バインドパラメータ名は変換されません。
	 * <a href="https://future-architect.github.io/uroborosql-doc/why_uroborosql/"
	 * >GitHub: uroboroSQL (ウロボロスキュール)</a>
	 * </pre>
	 */
	public static SqlAgent dao() {
		return daoThreadLocal.get();
	}
	
	/**
	 * isValid の条件が false の場合は、例外をスローし、現在表示されている JSP にフォワードします。
	 * @param isValid 入力チェックなど、正しい場合に true となる条件
	 * @param message 上記が false の時に例外がスローされ、リクエスト属性に "message" としてセットされます。
	 * @param args メッセージの %s や %d に String#format で埋め込む文字列
	 */
	public static void valid(boolean isValid, String message, Object... args) {
		if (!isValid) {
			throw new IllegalArgumentException(String.format(message, args));
		}
	}
	
	//-------------------------------------------------------------------------
	
	/** カレントスレッドのトランザクション境界内で DAO インスタンスを保持 */
	private static final ThreadLocal<SqlAgent> daoThreadLocal = new ThreadLocal<>();
	
	/** DAO 接続設定 */
	private SqlConfig daoConfig;

	/** Servlet 呼び出し前後のフィルター処理 */
	@Override @SneakyThrows
	protected void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain) {
		if (req.getRequestURI().matches(".+\\.[^\\.]{3,4}")) {
			super.doFilter(req, res, chain); // webapp 配下の css などの静的リソース
			return;
		}
		if (invalidCsrfToken(req, res)) {
			req.getSession().setAttribute("message", "二重送信または不正なリクエストを無視しました。");
			res.sendRedirect(".");
			return;
		}
		req.setCharacterEncoding(StandardCharsets.UTF_8.name());
		
		// DB トランザクションの開始、コミット、ロールバック制御
		try (SqlAgent dao = daoConfig.agent()) {
			try {
				daoThreadLocal.set(dao);
				super.doFilter(req, res, chain); // 各 Servlet 呼び出し
			}
			// Servlet で例外が発生した場合は、ロールバックして、Exception 内容を message 属性にセット
			catch (Exception e) {
				dao.rollback();
				Throwable cause = ExceptionUtils.getRootCause(e);
				req.getSession().setAttribute("message", cause.getMessage());
				if (cause instanceof IllegalArgumentException) {
					req.getRequestDispatcher((String) req.getSession().getAttribute("jspPath")).forward(req, res);
				} else {
					res.sendRedirect(".");
					log.warn(e.getMessage(), e);
				}
			}
		}
	}

	/** @return 二重送信など CSRF トークン不正の場合は true */
	synchronized private boolean invalidCsrfToken(HttpServletRequest req, HttpServletResponse res) {
		HttpSession session = req.getSession();
		final String CSRF_NAME = "_csrf";
		String sesCsrf = (String) session.getAttribute(CSRF_NAME);
		String reqCsrf = stream(req.getCookies()).filter(e -> e.getName().equals(CSRF_NAME)).map(Cookie::getValue).findAny().orElse(null);
		String newCsrf = UUID.randomUUID().toString();
		session.setAttribute(CSRF_NAME, newCsrf); // Servlet API 5.1 未満の Cookie は SameSite 未対応のため直書き
		res.addHeader("Set-Cookie", format("%s=%s; %sHttpOnly; SameSite=Strict",CSRF_NAME, newCsrf, req.isSecure() ? "Secure; " : ""));
		return "POST".equals(req.getMethod()) && reqCsrf != null && !reqCsrf.equals(sesCsrf);
	}

	/** Servlet フィルター初期化時のデータベース接続情報と初期データの設定 */
	@Override @SneakyThrows
	public void init() {
		Class.forName("org.h2.Driver");
		daoConfig = UroboroSQL.builder("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "sa", "").build();
		try (SqlAgent dao = daoConfig.agent()) {
			dao.updateWith("""
					CREATE TABLE item (
						id INT AUTO_INCREMENT, name VARCHAR(30), release_date CHAR(10), face_auth BOOLEAN,
						PRIMARY KEY (id)
					);
					INSERT INTO item (name, release_date, face_auth) VALUES 
						('iPhone 13 Pro Docomo版','2022-09-11',true),
						('iPhone 13 Pro Max Docomo版','2022-12-05',true),
						('Xperia 1 IV 国内版','2022-07-22',false)
					;
				""").count();
		}
	}

	/** 最後にフォーワードした JSP を保存するフィルター */
	@WebFilter(urlPatterns = "*.jsp", dispatcherTypes = DispatcherType.FORWARD)
	public static class ForwardJspPathSaveFilter extends HttpFilter {
		@Override @SneakyThrows
		protected void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain) {
			req.getSession().setAttribute("jspPath", req.getServletPath());
			super.doFilter(req, res, chain);
		}
	}
}
