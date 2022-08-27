package jp.example.filter;

import static java.util.stream.Collectors.*;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
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

import jodd.servlet.DispatcherUtil;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * 自動フラッシュ属性とエラーを制御するフィルターです。
 * <pre>
 * リダイレクト時に、リクエスト属性をセッション経由でリダイレクト先のリクエスト属性に自動転送します。
 * アプリエラー時に自動フォワード、システムエラー時に自動リダイレクトします。
 * </pre>
 * 以下に、サーブレットで例外がスローされた場合の動作を示します。
 * <pre>
 * 1. AutoTransactionFilter を使用している場合はロールバックします。
 * 2. 例外メッセージを JSP 表示用にリクエスト属性 MESSAGE にセットします。
 * 3. IllegalStateException の場合、アプリエラーとしてセッション属性 APP_ERROR_FORWARD_PATH (通常は表示元) にフォワードします。
 * 4. 上記以外の例外の場合は、システムエラーとしてセッション属性 SYS_ERROR_REDIRECT_URL にリダイレクト (自動フラッシュ) します。
 * 5. セッションに APP_ERROR_FORWARD_PATH も SYS_ERROR_REDIRECT_URL も無い場合は、コンテキストルートにリダイレクト (自動フラッシュ)。
 * <prel>
 * @author New Gradle Project Wizard
 */
@Slf4j
public class AutoFlashFilter extends HttpFilter {
	
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
	 * エラーチェック用のメソッドです。<br>
	 * 指定した条件が false の場合、引数のメッセージを持つ IllegalStateException をスローします。
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
	 * 3. AutoCsrfFilter を使用している場合は、meta と form input hidden に name="_csrf" として CSRF トークンが埋め込まれます。
	 * </pre>
	 * @param jspPath JSP パス
	 */
	@SneakyThrows
	public static void forward(Object jspPath) {
		RequestContext context = requestContextThreadLocal.get();
		String path = ((String) jspPath).startsWith("/") ? (String) jspPath : "/WEB-INF/jsp/" + jspPath;
		log.debug("フォワード {}", path);
		context.req.getSession().setAttribute(APP_ERROR_FORWARD_PATH, path);
		context.req.getRequestDispatcher(path).forward(context.req, context.res);
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
	
	@Override @SneakyThrows
	protected void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain) {
		
		// html css js などを除外
		if (req.getRequestURI().contains(".")) {
			super.doFilter(req, res, chain); 
			return;
		}
		
		// リダイレクト時のフラッシュ属性をセッションからリクエスト属性に復元し、セッションから削除
		HttpSession session = req.getSession();
		@SuppressWarnings("unchecked")
		Map<String, Object> flashMap = (Map<String, Object>) session.getAttribute(FLASH_ATTRIBUTE);
		if (flashMap != null) {
			flashMap.forEach(req::setAttribute);
			session.removeAttribute(FLASH_ATTRIBUTE);
		}
		
		// リクエストのスレッドローカル設定と Servlet 呼び出し
		Stopwatch stopwatch = Stopwatch.createStarted();
		try {
			requestContextThreadLocal.set(new RequestContext(req, res));
			super.doFilter(req, res, chain);
			
		// ルート例外の getMessage() をリクエスト属性 MESSAGE にセットして JSP から参照できるようにする
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
			
		} finally {
			requestContextThreadLocal.remove();
			log.debug("処理時間 {}ms [{}] {} {}", stopwatch.elapsed(TimeUnit.MILLISECONDS), req.getMethod(), 
					DispatcherUtil.getFullUrl(req), Objects.toString(req.getAttribute(MESSAGE), ""));
		}
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
