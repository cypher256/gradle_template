package jp.example;

import static java.lang.String.*;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.servlet.FilterChain;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import com.google.common.base.Stopwatch;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import jodd.servlet.DispatcherUtil;
import jodd.servlet.filter.ByteArrayResponseWrapper;
import jp.co.future.uroborosql.SqlAgent;
import jp.co.future.uroborosql.UroboroSQL;
import jp.co.future.uroborosql.config.SqlConfig;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * Servlet JSP 単一レイヤーアーキテクチャーコントローラーのサンプルです。
 * このコントローラーは、実行行 150 行ほどの Servlet API の薄いラッパーとして構成されています。
 * <ul>
 * <li>シンプルな単一レイヤーアーキテクチャー: 分散開発や分散実行が不要なプロジェクト向け。
 * <li>トランザクション制御: サーブレットでの DB コネクションの取得・解放やロールバック不要。
 * <li>エラーメッセージ制御: IllegalStateException スローで message 属性にセットして、現在の JSP にフォワード。
 * <li>自動二重送信 CSRF チェック: 各 Servlet でのチェックや JSP への埋め込み不要。
 * <li>自動フラッシュ属性: リダイレクト時はリクエスト属性を自動的にセッション経由で、リダイレクト先のリクエスト属性に転送。
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
		return daoThreadLocal.get();
	}
	
	/**
	 * Servlet で使用するエラーチェック用のメソッドです。
	 * isLegal が false の場合は Illegal として例外をスローし、セッション属性 FORWARD_PATH にフォワードします。
	 * デフォルトでは、最後に {@link #forward(Object)} した jsp のパスが FORWARD_PATH にセットされています。
	 * @param isLegal 入力チェックなどが正しい場合に true となる条件
	 * @param message 上記が false の時に例外がスローされ、例外メッセージがリクエスト属性にセットされます。
	 * @param args メッセージの %s や %d に String#format で埋め込む文字列
	 */
	public static void valid(boolean isLegal, String message, Object... args) {
		if (!isLegal) {
			throw new IllegalStateException(String.format(message, args));
		}
	}
	
	/**
	 * JSP にフォワードします。
	 * form 開始タグ (post) の下に CSRF トークンの hidden が自動追加されます。
	 * @param jspPath JSP パス。先頭がスラッシュでない場合は "/WEB-INF/jsp/" が先頭に追加されます。
	 */
	@SneakyThrows
	public static void forward(Object jspPath) {
		String path = jspPath.toString();
		if (!path.startsWith("/")) path = "/WEB-INF/jsp/" + jspPath;
		
		// 生成した CSRF_TOKEN、指定された FORWARD_PATH をセッション属性に保存
		RequestContext context = requestContextThreadLocal.get();
		HttpSession session = context.req.getSession();
		String newCsrf = UUID.randomUUID().toString();
		session.setAttribute(CSRF_TOKEN, newCsrf);
		session.setAttribute(FORWARD_PATH, path);
		log.debug("[FORWARD_PATH] {}", path);
		
		// JSP にフォワードして、処理後の HTML form に hidden として CSRF トークン埋め込み
		ByteArrayResponseWrapper tempRes = new ByteArrayResponseWrapper(context.res);
		context.req.getRequestDispatcher(path).forward(context.req, tempRes);
		String html = new String(tempRes.toByteArray(), tempRes.getCharacterEncoding());
		html = html.replaceAll("(?si)([ \t]*)(<form[^>]*post[^>]*>)", 
			format("$1$2\n$1\t<input type=\"hidden\" name=\"%s\" value=\"%s\">", CSRF_TOKEN, newCsrf));
		context.res.getWriter().write(html);
	}
	
	/**
	 * リダイレクトします。
	 * 現在のリクエスト属性は、フラッシュ属性としてセッション経由でリダイレクト先に引き継がれます。
	 * @param redirectUrl リダイレクト先 URL。null の場合はコンテキストルート。
	 */
	@SneakyThrows
	public static void redirect(Object redirectUrl) {
		RequestContext context = requestContextThreadLocal.get();
		String url = redirectUrl == null ? context.req.getContextPath() : redirectUrl.toString();
		context.req.getSession().setAttribute(REDIRECT_URL, url);
		context.res.sendRedirect(url);
		
		// リクエスト属性をフラッシュ属性としてセッションに保存 (リダイレクト後に削除される)
		if (!url.contains("//")) {
			context.req.getSession().setAttribute(FLASH_ATTRIBUTE, 
					Collections.list(context.req.getAttributeNames()).stream()
						.collect(Collectors.toMap(name -> name, context.req::getAttribute)));
		}
	}
	
	//-------------------------------------------------------------------------
	// Servlet フィルター処理
	//-------------------------------------------------------------------------
	
	public static final String MESSAGE = "_message";
	public static final String FORWARD_PATH = "_forward_path";
	public static final String REDIRECT_URL = "_redirect_url";
	public static final String CSRF_TOKEN = "_csrf";
	public static final String FLASH_ATTRIBUTE = "_flash_attribute";
	private static final ThreadLocal<RequestContext> requestContextThreadLocal = new ThreadLocal<>();
	private static final ThreadLocal<SqlAgent> daoThreadLocal = new ThreadLocal<>();
	private SqlConfig daoConfig;
	private HikariDataSource dataSource;
	
	@AllArgsConstructor
	private static class RequestContext {
		final HttpServletRequest req;
		final HttpServletResponse res;
	}

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
	@Override @SneakyThrows @SuppressWarnings("unchecked")
	protected void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain) {
		
		// css や js など拡張子がある静的リソースを除外
		if (req.getRequestURI().matches(".+\\.[^\\.]{3,4}")) {
			super.doFilter(req, res, chain);
			return;
		}
		
		// リダイレクト時のフラッシュ属性をセッションからリクエスト属性に戻して、セッションから削除
		HttpSession session = req.getSession();
		Map<String, Object> flashMap = (Map<String, Object>) session.getAttribute(FLASH_ATTRIBUTE);
		if (flashMap != null) {
			flashMap.forEach(req::setAttribute);
			session.removeAttribute(FLASH_ATTRIBUTE);
		}
		req.setCharacterEncoding(StandardCharsets.UTF_8.name()); // post エンコーディング (getParameter 前)
		requestContextThreadLocal.set(new RequestContext(req, res));
		
		// CSRF トークンチェック (JSP form 自動付加)
		String reqCsrf = req.getParameter(CSRF_TOKEN);
		String sesCsrf = (String) session.getAttribute(CSRF_TOKEN);
		if ("POST".equals(req.getMethod()) && !StringUtils.equals(reqCsrf, sesCsrf)) {
			req.setAttribute(MESSAGE, "二重送信または期限切れリクエストを無視しました。");
			redirect(session.getAttribute(REDIRECT_URL));
			return;
		}
		
		// DB トランザクションブロック (正常時はコミット、例外発生時はロールバック)
		try (SqlAgent dao = daoConfig.agent()) {
			Stopwatch stopwatch = Stopwatch.createStarted();
			try {
				daoThreadLocal.set(dao);
				super.doFilter(req, res, chain); // 各 Servlet 呼び出し
			} catch (Throwable e) {
				dao.rollback();
				
				// 例外内容を message 属性にセットして、現在表示されている JSP にフォワード
				Throwable cause = ExceptionUtils.getRootCause(e);
				req.setAttribute(MESSAGE, cause.getMessage());
				String forwardPath = (String) session.getAttribute(FORWARD_PATH);
				if (forwardPath != null && cause instanceof IllegalStateException) {
					forward(forwardPath);
				} else {
					redirect(session.getAttribute(REDIRECT_URL));
					log.warn(e.getMessage(), e);
				}
			} finally {
				daoThreadLocal.remove();
				requestContextThreadLocal.remove();
				log.debug("処理時間 {} {}", stopwatch, DispatcherUtil.getFullUrl(req));
			}
		}
	}
}
