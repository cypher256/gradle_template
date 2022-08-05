package jp.example;

import static java.lang.String.*;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.Optional;
import java.util.UUID;

import javax.servlet.FilterChain;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.lang3.exception.ExceptionUtils;

import com.google.common.base.Stopwatch;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import jodd.servlet.DispatcherUtil;
import jodd.servlet.ServletUtil;
import jp.co.future.uroborosql.SqlAgent;
import jp.co.future.uroborosql.UroboroSQL;
import jp.co.future.uroborosql.config.SqlConfig;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * Servlet JSP 単一レイヤーアーキテクチャーコントローラーのサンプルです。<br>
 * サンプルは、Servlet API の薄いラッパーとして構成されており、以下のポリシーに基づいています。
 * <ul>
 * <li>シンプルな単一レイヤーアーキテクチャー: 分散開発や分散実行が不要なプロジェクト向け。
 * <li>トランザクション制御: サーブレットでの DB コネクションの取得・解放やロールバック不要。
 * <li>エラーメッセージ制御: IllegalStateException スローで message 属性にセットして、現在の JSP にフォワード。
 * <li>二重送信など CSRF 自動チェック: 各 Servlet でのチェックや JSP への埋め込み不要。
 * </ul>
 * @author Pleiades All in One New Gradle Project Wizard (EPL)
 */
@WebFilter("/*")
@Slf4j
public class SingleTierController extends HttpFilter {
	
	//-------------------------------------------------------------------------
	// Servlet から使用するショートカット static メソッド
	//-------------------------------------------------------------------------
	
	/**
	 * Servlet で使用する DAO インスタンスを取得します。
	 * <pre>
	 * 自動採番の主キーを持つテーブは、id などのエンティティに関するアノテーションは不要です。
	 * スネークケース、キャメルケースは自動変換されます。ただし、バインドパラメータ名は変換されません。
	 * <a href="https://future-architect.github.io/uroborosql-doc/why_uroborosql/"
	 * >GitHub: uroboroSQL (ウロボロスキュール)</a>
	 * </pre>
	 * @return トランザクション境界内の DAO 兼トランザクションマネージャー
	 */
	public static SqlAgent dao() {
		return requestContextThreadLocal.get().dao;
	}
	
	/**
	 * Servlet で使用するエラーチェック用のメソッドです。<br>
	 * isValid の条件が false の場合は例外をスローし、現在表示されている JSP にフォワードします。
	 * @param isValid 入力チェックなどが正しい場合に true となる条件
	 * @param message 上記が false の時に例外がスローされ、例外メッセージが属性に "message" としてセットされます。
	 * @param args メッセージの %s や %d に String#format で埋め込む文字列
	 */
	public static void valid(boolean isValid, String message, Object... args) {
		if (!isValid) {
			throw new IllegalStateException(String.format(message, args));
		}
	}
	
	/**
	 * JSP にフォワードします。
	 * @param jspPath "/WEB-INF/jsp/" に続く JSP パス文字列
	 */
	@SneakyThrows
	public static void forward(Object jspPath) {
		String path = "/WEB-INF/jsp/" + jspPath;
		RequestContext context = requestContextThreadLocal.get();
		context.req.getSession().setAttribute("forwardPath", path);
		context.req.getRequestDispatcher(path).forward(context.req, context.res);
	}
	
	/**
	 * リダイレクトします。
	 * @param url リダイレクト先 URL
	 */
	@SneakyThrows
	public static void redirect(Object url) {
		RequestContext context = requestContextThreadLocal.get();
		context.req.getSession().setAttribute("redirectUrl", url);
		context.res.sendRedirect(url.toString());
	}
	
	//-------------------------------------------------------------------------
	// Servlet フィルター処理
	//-------------------------------------------------------------------------
	
	@AllArgsConstructor
	private static class RequestContext {
		final HttpServletRequest req;
		final HttpServletResponse res;
		final SqlAgent dao;
	}
	
	private static final ThreadLocal<RequestContext> requestContextThreadLocal = new ThreadLocal<>();
	private SqlConfig daoConfig;
	private HikariDataSource dataSource;

	/** Web アプリ起動時のデータベース初期化 */
	@Override @SneakyThrows
	public void init() {
		dataSource = new HikariDataSource(new HikariConfig("/database.properties"));
		daoConfig = UroboroSQL.builder(dataSource).build();
		try (SqlAgent dao = daoConfig.agent()) {
			dao.update("create_table").count(); // ファイル実行 /src/main/resources/sql/create_table.sql
		}
	}
	
	/** Web アプリ終了時のデータベースリソース破棄 */
	@Override @SneakyThrows
	public void destroy() {
		dataSource.close();
		DriverManager.deregisterDriver(DriverManager.getDrivers().nextElement()); // Tomcat 警告抑止
	}

	/** すべての Servlet 呼び出しのフィルター処理 */
	@Override @SneakyThrows
	protected void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain) {
		HttpSession session = req.getSession();
		if (req.getRequestURI().matches(".+\\.[^\\.]{3,4}")) {
			super.doFilter(req, res, chain); // css や js など拡張子がある静的リソースを除外
			return;
		}
		if (invalidCsrfToken(req, res)) {
			session.setAttribute("message", "二重送信または不正なリクエストを無視しました。");
			res.sendRedirect(req.getContextPath());
			return;
		}
		req.setCharacterEncoding(StandardCharsets.UTF_8.name()); // post エンコーディング指定
		Stopwatch stopwatch = Stopwatch.createStarted();
		
		// DB トランザクションブロック (正常時はコミット、例外発生時はロールバック)
		try (SqlAgent dao = daoConfig.agent()) {
			try {
				requestContextThreadLocal.set(new RequestContext(req, res, dao));
				super.doFilter(req, res, chain); // 各 Servlet 呼び出し
			} catch (Throwable e) {
				dao.rollback();
				
				// 例外内容を message 属性にセットして、現在表示されている JSP にフォワード
				Throwable cause = ExceptionUtils.getRootCause(e);
				session.setAttribute("message", cause.getMessage());
				String forwardPath = (String) session.getAttribute("forwardPath");
				if (forwardPath != null && cause instanceof IllegalStateException) {
					req.getRequestDispatcher(forwardPath).forward(req, res);
				} else {
					String redirectUrl = (String) session.getAttribute("redirectUrl");
					res.sendRedirect(redirectUrl == null ? req.getContextPath() : redirectUrl);
					log.warn(e.getMessage(), e);
				}
			} finally {
				requestContextThreadLocal.remove(); // これが無いと Tomcat 終了時に重大エラー
			}
		}
		log.debug("処理時間 {} - {}", stopwatch, DispatcherUtil.getFullUrl(req));
	}

	/** @return 二重送信など CSRF トークン不正の場合は true */
	synchronized private boolean invalidCsrfToken(HttpServletRequest req, HttpServletResponse res) {
		final String CSRF = "_csrf";
		String reqCsrf = Optional.ofNullable(ServletUtil.getCookie(req, CSRF)).map(Cookie::getValue).orElse(null);
		String sesCsrf = (String) req.getSession().getAttribute(CSRF);
		String newCsrf = UUID.randomUUID().toString();
		req.getSession().setAttribute(CSRF, newCsrf); // Servlet 5.1 未満の Cookie クラスは SameSite 未対応のため直書き
		res.addHeader("Set-Cookie", format("%s=%s; %sHttpOnly; SameSite=Strict", CSRF, newCsrf, req.isSecure() ? "Secure; " : ""));
		return "POST".equals(req.getMethod()) && reqCsrf != null && !reqCsrf.equals(sesCsrf);
	}
}
