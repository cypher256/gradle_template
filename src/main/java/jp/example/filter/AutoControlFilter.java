package jp.example.filter;

import static java.lang.String.*;
import static java.util.stream.Collectors.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.servlet.FilterChain;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import com.google.common.base.Stopwatch;
import com.pivovarit.function.ThrowingConsumer;

import jodd.servlet.DispatcherUtil;
import jodd.servlet.ServletUtil;
import jodd.servlet.filter.ByteArrayResponseWrapper;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * 自動制御フィルターです。
 * <pre>
 * 自動フラッシュ属性: リダイレクト時は、リクエスト属性をセッション経由でリダイレクト先のリクエスト属性に自動転送。
 * 自動 CSRF トークン: .jsp と .html の hidden、meta、Cookie にトークン自動埋め込み。form 送信、Angular，axios などの対応不要。
 * 自動同期トークン: CSRF トークンを同期トークンとして画面遷移ごと (AJAX アクセス除く) に生成。
 * エラー時の表示元 JSP へ自動フォワード: new IllegalStateException(画面メッセージ) スローで、表示元 JSP にフォワード。
 * </pre>
 * @author New Gradle Project Wizard
 */
@Slf4j
public class AutoControlFilter extends HttpFilter {
	
	//-------------------------------------------------------------------------
	// Servlet から使用する public static 定数とメソッド
	//-------------------------------------------------------------------------

	/** リクエスト属性名: 画面に表示するメッセージ (サーブレットから自分でセット、エラー時は例外メッセージがセットされる) */
	public static final String MESSAGE = "MESSAGE";
	
	/** セッション属性名: アプリエラー時のフォワード先パス (デフォルトは表示元、変更する場合はサーブレットでセット) */
	public static final String APP_ERROR_FORWARD_PATH = "APP_ERROR_FORWARD_PATH";
	
	/** セッション属性名: システムエラー時のリダイレクト先 URL (デフォルトは最後のリダイレクト先、変更する場合はサーブレットでセット) */
	public static final String SYS_ERROR_REDIRECT_URL = "SYS_ERROR_REDIRECT_URL";
	
	/**
	 * エラーチェック用のメソッドです。
	 * <pre>
	 * 指定した条件が false の場合、引数のメッセージを持つ IllegalStateException をスローします。
	 * 以下、このメソッド以外でも適用される、サーブレットで例外がスローされた場合の共通動作です。
	 * 
	 * 1. AutoTransactionFilter によりロールバックされ、例外の getMessage() がリクエスト属性 MESSAGE にセットされます。
	 * 2. IllegalStateException の場合、アプリエラーとしてセッション属性 APP_ERROR_FORWARD_PATH (通常は表示元) にフォワードされます。
	 * 3. 上記以外の例外の場合は、システムエラーとしてセッション属性 SYS_ERROR_REDIRECT_URL にリダイレクト (自動フラッシュ) されます。
	 * 4. セッションに APP_ERROR_FORWARD_PATH も SYS_ERROR_REDIRECT_URL も無い場合は、コンテキストルートにリダイレクト (自動フラッシュ)。
	 * <prel>
	 * @param isValid 入力チェックなどが正しい場合に true となる条件
	 * @param message リクエスト属性にセットするメッセージ
	 * @param args メッセージの %s や %d に String#format で埋め込む文字列
	 */
	public static void valid(boolean isValid, String message, Object... args) {
		if (!isValid) {
			throw new IllegalStateException(String.format(message, args));
		}
	}
	
	/**
	 * JSP にフォワードします。
	 * <pre>
	 * 標準の req.getRequestDispatcher(path).forward(req, res) の代わりに使用します。
	 * JSP 以外へのフォワードは上記の標準のメソッドを使用してください。このメソッドは、以下の処理を行います。
	 * 
	 * 1. 先頭がスラッシュの場合はそのまま、スラッシュでない場合は "/WEB-INF/jsp/" + jspPath をフォワード先パスとしてフォワードします。
	 * 2. フォワード先パスをセッション属性 APP_ERROR_FORWARD_PATH に保存します (入力エラーなどのアプリエラー時のフォワード先として使用)。
	 * 3. JSP 処理後の HTML の meta と form input hidden に name="_csrf" として CSRF トークンを埋め込みます。
	 * </pre>
	 * CSRF トークンの扱い
	 * <pre>
	 * form サブミット、form 内容の JavaScript 送信、Angular、axios の場合は、自動的にリクエストに含まれるため、何もする必要はありません。
	 * JavaScript で手動で設定する場合は、下記のいずれかで取得し、post リクエストヘッダー X-XSRF-TOKEN にセットする必要があります。
	 * 
	 *     // form の hidden から取得 (method="post" の form がある画面のみ)
	 *     document.forms[0]._csrf.value
	 *     // meta タグから取得する場合 (すべての画面)
	 *     document.querySelector("meta[name='_csrf']").content
	 *     // Cookie から取得する場合 (すべての画面)
	 *     document.cookie.split('; ').find(e => e.startsWith('XSRF-TOKEN')).split('=')[1]
	 * 
	 * アップロード用の multipart post form の場合は、hidden が getParameter で取得できないため、action 属性にクエリー文字列として
	 * 指定する必要があります。リクエストパラメーター名は _csrf とし、値は ${_csrf} で取得できまます。
	 * 
	 *     form タグ
	 *     action="/upload?_csrf=${_csrf}" method="post" enctype="multipart/form-data"
	 * </pre>
	 * @param jspPath JSP パス
	 */
	@SneakyThrows
	public static void forward(Object jspPath) {
		RequestContext context = requestContextThreadLocal.get();
		String path = ((String) jspPath).startsWith("/") ? (String) jspPath : "/WEB-INF/jsp/" + jspPath;
		log.debug("フォワード {}", path);
		context.req.getSession().setAttribute(APP_ERROR_FORWARD_PATH, path);
		embedToken(_res -> context.req.getRequestDispatcher(path).forward(context.req, _res));
	}
	
	/**
	 * リダイレクトします。
	 * <pre>
	 * 標準の res.sendRedirect(url) の代わりに使用します。
	 * 別のアプリや外部サイトへのリダイレクトは上記の標準のメソッドを使用してください。このメソッドは、以下の処理を行います。
	 * 
	 * 1. 指定した redirectUrl (null の場合はコンテキストルート) にリダイレクトします。
	 * 2. リダイレクト先 URL をセッション属性 SYS_ERROR_REDIRECT_URL に保存します (システムエラー時のリダイレクト先として使用)。
	 * 3. 現在のリクエスト属性をフラッシュ属性としてセッションに保存します (リダイレクト後にリクエスト属性に復元)。
	 * </pre>
	 * @param redirectUrl リダイレクト先 URL
	 */
	@SneakyThrows
	public static void redirect(Object redirectUrl) {
		RequestContext context = requestContextThreadLocal.get();
		HttpServletRequest req = context.req;
		String url = Objects.toString(redirectUrl, req.getContextPath());
		log.debug("リダイレクト {}", url);
		context.res.sendRedirect(url);
		req.getSession().setAttribute(SYS_ERROR_REDIRECT_URL, url);
		req.getSession().setAttribute(FLASH_ATTRIBUTE, Collections.list(req.getAttributeNames()).stream()
				.filter(name -> !name.endsWith(".FILTERED")) // Apache Shiro 関連除外 (リダイレクト後の shiro タグ不良対応)
				.collect(toMap(name -> name, req::getAttribute)));
	}
	
	//-------------------------------------------------------------------------
	// Servlet フィルター処理
	//-------------------------------------------------------------------------
	
	private static final String FLASH_ATTRIBUTE = "FLASH_ATTRIBUTE";
	private static final String _csrf = "_csrf";
	private static final ThreadLocal<RequestContext> requestContextThreadLocal = new ThreadLocal<>();
	
	/** ThreadLocal に保存するリクエストコンテキストクラス */
	@AllArgsConstructor
	private static class RequestContext {
		final HttpServletRequest req;
		final HttpServletResponse res;
	}

	/** 共通エンコーディング設定 */
	@Override @SneakyThrows
	public void init() {
		ServletContext sc = getServletContext();
		sc.setRequestCharacterEncoding(StandardCharsets.UTF_8.name()); // post getParameter エンコーディング
		sc.setResponseCharacterEncoding(StandardCharsets.UTF_8.name()); // AJAX レスポンス
	}
	
	/** リクエストコンテキストのスレッドローカル設定 */
	@Override @SneakyThrows
	protected void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain) {
		String uri = req.getRequestURI();
		if (!uri.endsWith(".html") && uri.contains(".")) { // css js など
			super.doFilter(req, res, chain);
			return;
		}
		Stopwatch stopwatch = Stopwatch.createStarted();
		try {
			requestContextThreadLocal.set(new RequestContext(req, res));
			process(req, res, chain);
		} finally {
			requestContextThreadLocal.remove();
			log.debug("処理時間 {}ms [{}] {} {}", stopwatch.elapsed(TimeUnit.MILLISECONDS), req.getMethod(), 
					DispatcherUtil.getFullUrl(req), Objects.toString(req.getAttribute(MESSAGE), ""));
		}
	}

	/** 状態チェック、フラッシュ属性転送、エラー制御 */
	@SneakyThrows
	protected void process(HttpServletRequest req, HttpServletResponse res, FilterChain chain) {
		
		// POST トークンチェック
		HttpSession session = req.getSession();
		if (notMatchPostToken(req, res)) {
			req.setAttribute(MESSAGE, "不正なデータが送信されました。");
			sendAjaxOr(() -> redirect(session.getAttribute(SYS_ERROR_REDIRECT_URL)));
			return;
		}

		// リダイレクト時のフラッシュ属性をセッションからリクエスト属性に復元し、セッションから削除
		@SuppressWarnings("unchecked")
		Map<String, Object> flashMap = (Map<String, Object>) session.getAttribute(FLASH_ATTRIBUTE);
		if (flashMap != null) {
			flashMap.forEach(req::setAttribute);
			session.removeAttribute(FLASH_ATTRIBUTE);
		}

		// 次のフィルターへ
		try {
			if (req.getRequestURI().endsWith(".html")) {
				embedToken(_res -> super.doFilter(req, _res, chain)); // html (JSP 構文使用可能)
			} else {
				super.doFilter(req, res, chain); // Servlet
			}
		
		// ルート例外の getMessage() をリクエスト属性 MESSAGE にセットして画面に表示できるようにする
		} catch (Throwable e) {
			Throwable cause = ExceptionUtils.getRootCause(e);
			req.setAttribute(MESSAGE, cause.getMessage());
			String forwardPath = (String) session.getAttribute(APP_ERROR_FORWARD_PATH);
			if (cause instanceof IllegalStateException && forwardPath != null) {
				// アプリエラー (入力エラーなどの業務エラー)
				sendAjaxOr(() -> forward(forwardPath));
			} else {
				// システムエラー (DB エラーなど)
				sendAjaxOr(() -> redirect(session.getAttribute(SYS_ERROR_REDIRECT_URL)));
				log.warn(cause.getMessage(), cause);
			}
		}
	}

	/**
	 * post 時のトークンをチェックします (並行リクエスト対応のため synchronized)。
	 * @return トークンが一致しない場合は true
	 */
	synchronized protected boolean notMatchPostToken(HttpServletRequest req, HttpServletResponse res) throws IOException {
		
		// トークンチェック (リクエスト、ヘッダー、Cookie は標準的な名前を使用)
		HttpSession session = req.getSession();
		if ("POST".equals(req.getMethod())) {
			String sesCsrf = (String) session.getAttribute(_csrf);
			String reqCsrf = StringUtils.firstNonEmpty(
				req.getParameter(_csrf), 		// form hidden "_csrf" → フォームサブミットやフォームベースの AJAX
				req.getHeader("X-CSRF-TOKEN"),	// meta "_csrf" → jQuery などで meta タグからの手動セットでよく使われる名前
				req.getHeader("X-XSRF-TOKEN")	// Cookie "XSRF-TOKEN" → Angular、axios などで自動的に使用される名前
			);
			if (reqCsrf == null || !reqCsrf.equals(sesCsrf)) {
				return true; // エラー
			}
		}
		
		// 画面遷移の場合はトークンを生成し直し
		if (!isAjax() || session.getAttribute(_csrf) == null) {
			// * 同期トークンとして機能する (ただし、意図的に bfcache 無効化により、戻るボタンからの送信は正常に機能する)
			// * 二重送信やリロード多重送信も検出できるが UX 向上のため、JavaScript でボタン disabled および PRG パターンで防止推奨
			session.setAttribute(_csrf, UUID.randomUUID().toString());
		}
		
		// AJAX 参照用 Cookie 書き込み
		// * Secure: isSecure で https 判定。リバースプロキシの場合は RemoteIpFilter で連携。
		// * SameSite: Strict (送信は同一サイトのみ)。ブラウザのデフォルトは Lax (別サイトから GET 可能、POST 不可)。
		// * HttpOnly: 指定なし。JavaScript から参照可能にするために指定しない。
		res.addHeader("Set-Cookie", format("XSRF-TOKEN=%s;%sSameSite=Strict;", 
				session.getAttribute(_csrf), req.isSecure() ? " Secure;" : ""));
		
		// Cache-Control で bfcache 無効化 (ブラウザ戻るボタンでの get ページ表示はキャッシュではなくサーバ再取得するようにする)
		ServletUtil.preventCaching(res);
		return false; // 正常
	}
	
	/** HTML へのトークン埋め込み (login.html など .html も対象) */
	@SneakyThrows
	private static void embedToken(ThrowingConsumer<HttpServletResponse, Exception> responseConsumer) {
		RequestContext context = requestContextThreadLocal.get();
		ByteArrayResponseWrapper resWrapper = new ByteArrayResponseWrapper(context.res);
		responseConsumer.accept(resWrapper);
		String csrfToken = (String) context.req.getSession().getAttribute(_csrf);
		String html = new String(resWrapper.toByteArray(), resWrapper.getCharacterEncoding())
			.replaceFirst("(?i)(<head>)", format("""
				$1\n<meta name="_csrf" content="%s">""", csrfToken))
			.replaceAll("(?is)([ \t]*)(<form[^>]+post[^>]+>)", format("""
				$1$2\n$1\t<input type="hidden" name="_csrf" value="%s">""", csrfToken));
		context.res.setContentLength(html.getBytes(resWrapper.getCharacterEncoding()).length);
		context.res.getWriter().print(html);
	}

	/**
	 * AJAX リクエストの場合は MESSAGE を返却、そうでない場合は nonAjaxAction 実行します。
	 * @param nonAjaxAction AJAX でない場合の処理
	 */
	@SneakyThrows
	protected void sendAjaxOr(Runnable nonAjaxAction) {
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
	protected boolean isAjax() {
		HttpServletRequest req = requestContextThreadLocal.get().req;
		return "XMLHttpRequest".equals(req.getHeader("X-Requested-With")) ||
				StringUtils.contains(req.getHeader("Accept"), "/json");
	}
}
