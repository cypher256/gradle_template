package jp.example;

import static java.lang.String.*;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.servlet.FilterChain;
import javax.servlet.ServletContext;
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
 * Servlet JSP 単層アーキテクチャーで構成されたフロントコントローラーサンプルです。
 * このフロントコントローラーは、有効行 200 行に満たない Servlet API の薄いラッパーとなっています。
 * <ul>
 * <li>単層アーキテクチャー: 多層に対して、シンプルで直感的、管理・保守が容易。クラウドなどでの分散が不要なプロジェクト向け。
 * <li>自動フラッシュ属性: リダイレクト時は、リクエスト属性をセッション経由でリダイレクト先のリクエスト属性に自動転送。
 * <li>自動 CSRF トークン: hidden 埋め込み不要、form ベース AJAX 対応不要。Angular，Axios などでも対応不要。
 * <li>自動トランザクション: DB コネクションの取得・解放の考慮不要。例外スローでロールバックし、例外にセットしたメッセージを画面に表示。
 * <li>エラー時の表示元 JSP へ自動フォワード: new IllegalArgumentException(画面メッセージ) スローで、表示元 JSP にフォワード。
 * </ul>
 * @author Pleiades All in One (License MIT: https://opensource.org/licenses/MIT)
 */
@WebFilter("/*")
@Slf4j
public class SingleTierController extends HttpFilter {
	
	//-------------------------------------------------------------------------
	// Servlet から使用する static メソッド
	//-------------------------------------------------------------------------
	
	/**
	 * uroboroSQL DAO インスタンスを取得します。
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
	 * エラーチェック用のメソッドです。
	 * 指定した条件が false の場合、引数のメッセージを持つ IllegalArgumentException がスローされます。
	 * 以下、このメソッド以外にも適用される、サーブレットで例外がスローされた場合の共通動作です。
	 * <pre>
	 * 1. 例外の種類に関わらずロールバックされ、例外の getMessage() がリクエスト属性 MESSAGE にセットされます。
	 * 2. IllegalArgumentException の場合、アプリエラーとしてセッション属性 FORWARD_PATH (通常は表示元) にフォワードされます。
	 * 3. 上記以外の例外の場合は、システムエラーとしてセッション属性 REDIRECT_URL にリダイレクト (自動フラッシュ) されます。
	 * 4. セッションに FORWARD_PATH も REDIRECT_URL も無い場合は、コンテキストルートにリダイレクト (自動フラッシュ) されます。
	 * <prel>
	 * @param isValid 入力チェックなどが正しい場合に true となる条件
	 * @param message リクエスト属性にセットするメッセージ
	 * @param args メッセージの %s や %d に String#format で埋め込む文字列
	 */
	public static void valid(boolean isValid, String message, Object... args) {
		if (!isValid) {
			throw new IllegalArgumentException(String.format(message, args));
		}
	}
	
	/**
	 * JSP にフォワードします。
	 * JSP 以外へのフォワードは標準の req.getRequestDispatcher(path).forward(req, res) を使用してください。
	 * <pre>
	 * 1. "/WEB-INF/jsp/" + 指定した jspPath をフォワード先パスとしてフォワードします。
	 * 2. フォワード先パスをセッション属性 FORWARD_PATH に保存します (入力エラーなどのアプリエラー時のフォワード先として使用)。
	 * 3. JSP 処理後の HTML の meta と form input hidden に name="_csrf" として CSRF トークンを埋め込みます。
	 * </pre>
	 * @param jspPath JSP パス
	 */
	@SneakyThrows
	public static void forward(Object jspPath) {
		RequestContext context = requestContextThreadLocal.get();
		String path = "/WEB-INF/jsp/" + jspPath;
		context.req.getSession().setAttribute(FORWARD_PATH, path);
		log.debug("[{}] {}", FORWARD_PATH, path);
		
		// CSRF トークン自動埋め込み: アップロード用の multipart form は未対応のため、手動で action 属性にクエリー文字列追加が必要
		// 例: <form action="/upload?_csrf=${_csrf}" method="post" enctype="multipart/form-data">
		ByteArrayResponseWrapper resWrapper = new ByteArrayResponseWrapper(context.res);
		context.req.getRequestDispatcher(path).forward(context.req, resWrapper);
		String csrfValue = (String) context.req.getSession().getAttribute(_csrf);
		String html = new String(resWrapper.toByteArray(), resWrapper.getCharacterEncoding())
			.replaceFirst("(?i)(<head>)", format("""
				$1\n<meta name="_csrf" content="%s">""", csrfValue))
			.replaceAll("(?is)([ \t]*)(<form[^>]*post[^>]*>)", format("""
				$1$2\n$1\t<input type="hidden" name="_csrf" value="%s">""", csrfValue));
		context.res.getWriter().print(html);
	}
	
	/**
	 * リダイレクトします。
	 * 別のアプリや外部サイトへのリダイレクトは標準の res.sendRedirect(url) を使用してください。
	 * <pre>
	 * 1. 指定した redirectUrl (null の場合はコンテキストルート) にリダイレクトします。
	 * 2. リダイレクト先 URL をセッション属性 REDIRECT_URL に保存します (システムエラー時のリダイレクト先として使用)。
	 * 3. 現在のリクエスト属性をフラッシュ属性としてセッションに保存します (リダイレクト後にリクエスト属性に復元)。
	 * </pre>
	 * @param redirectUrl リダイレクト先 URL
	 */
	@SneakyThrows
	public static void redirect(Object redirectUrl) {
		RequestContext context = requestContextThreadLocal.get();
		String url = Objects.toString(redirectUrl, context.req.getContextPath());
		context.res.sendRedirect(url);
		context.req.getSession().setAttribute(REDIRECT_URL, url);
		log.debug("[{}] {}", REDIRECT_URL, url);
		
		// リクエスト属性をフラッシュ属性としてセッションに一時保存 (リダイレクト後に復元)
		context.req.getSession().setAttribute(FLASH_ATTRIBUTE, 
			Collections.list(context.req.getAttributeNames()).stream()
				.collect(Collectors.toMap(name -> name, context.req::getAttribute)));
	}

	public static final String MESSAGE = "MESSAGE";
	public static final String FORWARD_PATH = "FORWARD_PATH";
	public static final String REDIRECT_URL = "REDIRECT_URL";
	public static final String FLASH_ATTRIBUTE = "FLASH_ATTRIBUTE";
	public static final String _csrf = "_csrf";
	
	//-------------------------------------------------------------------------
	// Servlet フィルター処理
	//-------------------------------------------------------------------------
	
	private static final ThreadLocal<RequestContext> requestContextThreadLocal = new ThreadLocal<>();
	private static final ThreadLocal<SqlAgent> daoThreadLocal = new ThreadLocal<>();
	private SqlConfig daoConfig;
	private HikariDataSource dataSource;
	
	/** ThreadLocal に保存するリクエストコンテキストクラス */
	@AllArgsConstructor
	private static class RequestContext {
		final HttpServletRequest req;
		final HttpServletResponse res;
	}

	/** Web アプリ起動時のデータベース初期化と共通エンコーディング設定 */
	@Override @SneakyThrows
	public void init() {
		dataSource = new HikariDataSource(new HikariConfig("/database.properties"));
		daoConfig = UroboroSQL.builder(dataSource).build();
		try (SqlAgent dao = daoConfig.agent()) {
			dao.update("create_table").count(); // ファイル実行 /src/main/resources/sql/create_table.sql
		}
		// デフォルトエンコーディングの設定 (Servlet 4.0 以降)
		ServletContext sc = getServletContext();
		sc.setRequestCharacterEncoding(StandardCharsets.UTF_8.name()); // post getParameter エンコーディング
		sc.setResponseCharacterEncoding(StandardCharsets.UTF_8.name()); // AJAX レスポンス
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
		
		// 拡張子がある (.css や .js など) 静的リソースを除外
		if (req.getRequestURI().matches(".+\\.[^\\.]{3,4}")) {
			super.doFilter(req, res, chain);
			return;
		}
		requestContextThreadLocal.set(new RequestContext(req, res));
		HttpSession session = req.getSession();
		if (session.isNew() && !req.getRequestURI().equals(req.getContextPath() + "/")) {
			req.setAttribute(MESSAGE, "セッションの有効期限が切れました。");
			sendAjaxOr(() -> redirect(req.getContextPath()));
			return;
		}
		
		// post 時の CSRF トークンチェック (標準的な名前を使用)
		synchronized (this) {
			String reqCsrf = StringUtils.firstNonEmpty(
				req.getParameter(_csrf), 		// form hidden "_csrf" → サブミットやフォームベースの AJAX
				req.getHeader("X-CSRF-TOKEN"),	// meta "_csrf" → jQuery などで meta タグからの手動セットでよく使われる名前
				req.getHeader("X-XSRF-TOKEN")	// Cookie "XSRF-TOKEN" → Angular、Axios などで自動的に使用される名前
			);
			String sesCsrf = (String) session.getAttribute(_csrf);
			if ("POST".equals(req.getMethod()) && !StringUtils.equals(reqCsrf, sesCsrf)) {
				// 登録などの二重送信も防げるが、403 エラーとなり UX 的に優れないため、JSP でも二度押し防止する
				res.sendError(HttpServletResponse.SC_FORBIDDEN);
				return;
			}
			if (!isAjax()) {
				// 画面遷移ごとに新しい CSRF トークン生成 (ブラウザの戻るなどによる不正画面遷移対策のトランザクショントークンとして使用)
				session.setAttribute(_csrf, UUID.randomUUID().toString());
			}
			// Cookie 書き込み
			// * Secure: 指定あり。localhost を除く https サーバのみに送信。指定に関係なくブラウザには返せるのでプロトコル判定しない。
			// * SameSite: 指定なし。モダンブラウザのデフォルトは Lax (別サイトから POST で送信不可、GET は送信可能)。
			// * HttpOnly: 指定なし。JavaScript から参照可能にするために指定しない。
			res.addHeader("Set-Cookie", format("XSRF-TOKEN=%s; Secure;", session.getAttribute(_csrf)));
		}
		
		// リダイレクト時のフラッシュ属性をセッションからリクエスト属性に復元し、セッションから削除
		Map<String, Object> flashMap = (Map<String, Object>) session.getAttribute(FLASH_ATTRIBUTE);
		if (flashMap != null) {
			flashMap.forEach(req::setAttribute);
			session.removeAttribute(FLASH_ATTRIBUTE);
		}
		
		// DB トランザクションブロック (正常時はコミット、例外発生時はロールバック)
		try (SqlAgent dao = daoConfig.agent()) {
			Stopwatch stopwatch = Stopwatch.createStarted();
			try {
				daoThreadLocal.set(dao);
				super.doFilter(req, res, chain); // 各 Servlet 呼び出し
			} catch (Throwable e) {
				dao.rollback();
				
				// ルート例外の getMessage() をリクエスト属性にセットしてフォワードまたはリダイレクト
				Throwable cause = ExceptionUtils.getRootCause(e);
				req.setAttribute(MESSAGE, cause.getMessage());
				String forwardPath = (String) session.getAttribute(FORWARD_PATH);
				if (cause instanceof IllegalArgumentException && forwardPath != null) {
					// 入力エラーなどのアプリエラー
					sendAjaxOr(() -> forward(forwardPath));
				} else {
					// システムエラー
					sendAjaxOr(() -> redirect(session.getAttribute(REDIRECT_URL)));
					log.warn(cause.getMessage(), cause);
				}
				
			} finally {
				daoThreadLocal.remove();
				requestContextThreadLocal.remove();
				log.debug("処理時間 {} [{}] {} {}", stopwatch, req.getMethod(), DispatcherUtil.getFullUrl(req),
						Objects.toString(req.getAttribute(MESSAGE), ""));
			}
		}
	}

	/** AJAX リクエストの場合は MESSAGE を返却、そうでない場合は nonAjaxAction 実行 */
	@SneakyThrows
	private void sendAjaxOr(Runnable nonAjaxAction) {
		if (isAjax()) {
			RequestContext context = requestContextThreadLocal.get();
			context.res.getWriter().print(context.req.getAttribute(MESSAGE));
		} else {
			nonAjaxAction.run();
		}
	}

	/** AJAX リクエストの場合は true */
	private boolean isAjax() {
		HttpServletRequest req = requestContextThreadLocal.get().req;
		return "XMLHttpRequest".equals(req.getHeader("X-Requested-With")) ||
				StringUtils.contains(req.getHeader("Accept"), "/json");
	}
}
