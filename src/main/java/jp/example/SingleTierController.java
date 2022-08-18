package jp.example;

import static java.lang.String.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
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
import jodd.servlet.ServletUtil;
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
	// Servlet から使用する public static 定数とメソッド
	//-------------------------------------------------------------------------

	/** リクエスト属性名: String 画面に表示するメッセージ (サーブレットから自分でセット、エラー時は例外メッセージがセットされる) */
	public static final String MESSAGE = "MESSAGE";
	
	/** セッション属性名: String アプリエラー時のフォワード先パス (デフォルトは表示元、変更する場合はサーブレットでセット) */
	public static final String APP_ERROR_FORWARD_PATH = "APP_ERROR_FORWARD_PATH";
	
	/** セッション属性名: String システムエラー時のリダイレクト先 URL (デフォルトは最後のリダイレクト先、変更する場合はサーブレットでセット) */
	public static final String SYS_ERROR_REDIRECT_URL = "SYS_ERROR_REDIRECT_URL";

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
	 * 指定した条件が false の場合、引数のメッセージを持つ IllegalArgumentException がスローします。
	 * 以下、このメソッド以外でも適用される、サーブレットで例外がスローされた場合の共通動作です。
	 * <pre>
	 * 1. 例外の種類に関わらずロールバックされ、例外の getMessage() がリクエスト属性 MESSAGE にセットされます。
	 * 2. IllegalArgumentException の場合、アプリエラーとしてセッション属性 APP_ERROR_FORWARD_PATH (通常は表示元) にフォワードされます。
	 * 3. 上記以外の例外の場合は、システムエラーとしてセッション属性 SYS_ERROR_REDIRECT_URL にリダイレクト (自動フラッシュ) されます。
	 * 4. セッションに APP_ERROR_FORWARD_PATH も SYS_ERROR_REDIRECT_URL も無い場合は、コンテキストルートにリダイレクト (自動フラッシュ)。
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
	 * 1. 先頭がスラッシュの場合はそのまま、スラッシュでない場合は "/WEB-INF/jsp/" + jspPath をフォワード先パスとしてフォワードします。
	 * 2. フォワード先パスをセッション属性 APP_ERROR_FORWARD_PATH に保存します (入力エラーなどのアプリエラー時のフォワード先として使用)。
	 * 3. JSP 処理後の HTML の meta と form input hidden に name="_csrf" として CSRF トークンを埋め込みます。
	 * </pre>
	 * CSRF トークンの扱い
	 * <pre>
	 * form サブミット、form の AJAX 送信、Angular、Axios の場合は、自動的にリクエストに含まれるため、何もする必要はありません。
	 * JavaScript で手動で設定する場合は下記で取得し、post リクエストヘッダー X-XSRF-TOKEN にセットする必要があります。
	 *     // meta タグから取得する場合 
	 *     document.querySelector("meta[name='_csrf']").content
	 *     // Cookie から取得する場合
	 *     document.cookie.split('; ').find(e => e.startsWith('XSRF-TOKEN')).split('=')[1]
	 * </pre>
	 * @param jspPath JSP パス
	 */
	@SneakyThrows
	public static void forward(Object jspPath) {
		RequestContext context = requestContextThreadLocal.get();
		String path = ((String) jspPath).startsWith("/") ? (String) jspPath : "/WEB-INF/jsp/" + jspPath;
		context.req.getSession().setAttribute(APP_ERROR_FORWARD_PATH, path);
		log.debug("[{}] {}", APP_ERROR_FORWARD_PATH, path);
		
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
	 * 2. リダイレクト先 URL をセッション属性 SYS_ERROR_REDIRECT_URL に保存します (システムエラー時のリダイレクト先として使用)。
	 * 3. 現在のリクエスト属性をフラッシュ属性としてセッションに保存します (リダイレクト後にリクエスト属性に復元)。
	 * </pre>
	 * @param redirectUrl リダイレクト先 URL
	 */
	@SneakyThrows
	public static void redirect(Object redirectUrl) {
		RequestContext context = requestContextThreadLocal.get();
		String url = Objects.toString(redirectUrl, context.req.getContextPath());
		context.res.sendRedirect(url);
		context.req.getSession().setAttribute(SYS_ERROR_REDIRECT_URL, url);
		log.debug("[{}] {}", SYS_ERROR_REDIRECT_URL, url);
		
		// リクエスト属性をフラッシュ属性としてセッションに一時保存 (リダイレクト後に復元)
		context.req.getSession().setAttribute(FLASH_ATTRIBUTE, 
			Collections.list(context.req.getAttributeNames()).stream()
				.collect(Collectors.toMap(name -> name, context.req::getAttribute)));
	}
	
	//-------------------------------------------------------------------------
	// Servlet フィルター処理
	//-------------------------------------------------------------------------
	
	public static final String FLASH_ATTRIBUTE = "FLASH_ATTRIBUTE";
	public static final String _csrf = "_csrf";
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

		// サーブレット呼び出し除外 (ファイル拡張子、セッション無し、CSRF エラー)
		if (req.getRequestURI().matches(".+\\.[^\\.]{2,5}")) { // 拡張子 (js css woff2 など) がある静的リソース
			super.doFilter(req, res, chain);
			return;
		}
		ServletUtil.preventCaching(res); // bfcache 無効化 (ブラウザ戻るボタンでの get ページ表示はサーバ再取得するようにする)
		requestContextThreadLocal.set(new RequestContext(req, res));
		HttpSession session = req.getSession();
		if (session.isNew() && !req.getRequestURI().equals(req.getContextPath() + "/")) {
			req.setAttribute(MESSAGE, "セッションの有効期限が切れました。");
			sendAjaxOr(() -> redirect(req.getContextPath()));
			return;
		}
		if (notMatchCsrfToken(req, res)) {
			// 二重送信も検出できるが UX 向上のため、送信前に JavaScript でボタンを押せなくするなどの二度押し防止推奨
			req.setAttribute(MESSAGE, "不正なデータが送信されました。");
			sendAjaxOr(() -> redirect(session.getAttribute(SYS_ERROR_REDIRECT_URL)));
			return;
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
				
				// ルート例外の getMessage() をリクエスト属性 MESSAGE にセットして画面に表示
				Throwable cause = ExceptionUtils.getRootCause(e);
				req.setAttribute(MESSAGE, cause.getMessage());
				String forwardPath = (String) session.getAttribute(APP_ERROR_FORWARD_PATH);
				if (cause instanceof IllegalArgumentException && forwardPath != null) {
					// 入力エラーなどのアプリエラー
					sendAjaxOr(() -> forward(forwardPath));
				} else {
					// システムエラー
					sendAjaxOr(() -> redirect(session.getAttribute(SYS_ERROR_REDIRECT_URL)));
					log.warn(cause.getMessage(), cause);
				}
				
			} finally {
				daoThreadLocal.remove();
				requestContextThreadLocal.remove();
				log.debug("処理時間 {}ms [{}] {} {}", stopwatch.elapsed(TimeUnit.MILLISECONDS), req.getMethod(), 
						DispatcherUtil.getFullUrl(req), Objects.toString(req.getAttribute(MESSAGE), ""));
			}
		}
	}

	/**
	 * post 時の CSRF トークンをチェックします (並行リクエスト対応のため synchronized)。
	 * リクエスト、ヘッダー、Cookie 名は標準的な名前を使用します。
	 * @return CSRF エラーの場合は true
	 */
	synchronized private boolean notMatchCsrfToken(HttpServletRequest req, HttpServletResponse res) throws IOException {
		HttpSession session = req.getSession();
		if ("POST".equals(req.getMethod())) {
			String sesCsrf = (String) session.getAttribute(_csrf);
			String reqCsrf = StringUtils.firstNonEmpty(
				req.getParameter(_csrf), 		// form hidden "_csrf" → フォームサブミットやフォームベースの AJAX
				req.getHeader("X-CSRF-TOKEN"),	// meta "_csrf" → jQuery などで meta タグからの手動セットでよく使われる名前
				req.getHeader("X-XSRF-TOKEN")	// Cookie "XSRF-TOKEN" → Angular、Axios などで自動的に使用される名前
			);
			if (reqCsrf == null || !reqCsrf.equals(sesCsrf)) {
				return true; // エラー
			}
		}
		if (!isAjax()) {
			// 画面遷移ごとのワンタイムトークン (リプレイアタック抑止であり、戻る抑止ではない)
			session.setAttribute(_csrf, UUID.randomUUID().toString());
		}
		// Cookie 書き込み
		// * Secure: 指定あり。localhost を除く https サーバのみに送信。指定に関係なくブラウザには返せるので isSecure 判定しない。
		// * SameSite: 指定なし。モダンブラウザのデフォルトは Lax (別サイトから POST で送信不可、GET は送信可能)。
		// * HttpOnly: 指定なし。JavaScript から参照可能にするために指定しない。
		res.addHeader("Set-Cookie", format("XSRF-TOKEN=%s; Secure;", session.getAttribute(_csrf)));
		return false; // 正常
	}

	/**
	 * AJAX リクエストの場合は MESSAGE を返却、そうでない場合は nonAjaxAction 実行します。
	 * @param nonAjaxAction AJAX でない場合の処理
	 */
	@SneakyThrows
	private void sendAjaxOr(Runnable nonAjaxAction) {
		if (isAjax()) {
			RequestContext context = requestContextThreadLocal.get();
			context.res.getWriter().print(context.req.getAttribute(MESSAGE));
		} else {
			nonAjaxAction.run();
		}
	}

	/**
	 * @return AJAX リクエストの場合は true
	 */
	private boolean isAjax() {
		HttpServletRequest req = requestContextThreadLocal.get().req;
		return "XMLHttpRequest".equals(req.getHeader("X-Requested-With")) ||
				StringUtils.contains(req.getHeader("Accept"), "/json");
	}
}
