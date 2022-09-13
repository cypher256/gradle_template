package jp.example.filter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.time.StopWatch;

import com.fasterxml.jackson.databind.ObjectMapper;

import jodd.servlet.DispatcherUtil;
import jodd.servlet.ServletUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * リダイレクト時の自動フラッシュと例外ハンドリングを行うフィルターです。
 * <pre>
 * セッションを使用した一般的なフラッシュスコープ実装です。このフィルターでは自動フラッシュ機構により、
 * デフォルトでは、特に意識することなく、リダイレクト前に設定したリクエスト属性がリダイレクト先でも、そのまま使用できます。
 * その他の機能として、アプリエラー時に入力画面に自動フォワード、システムエラー時に自動リダイレクトします。
 * </pre>
 * 以下に、Servlet で例外がスローされた場合の動作を示します。
 * <pre>
 * 1. ロールバックします。(AutoTransactionFilter を使用している場合)
 * 2. AJAX の場合、例外メッセージ文字列をレスポンスとしてクライアントに返して処理を終了します。そうでない場合、下記以降が実行されます。
 * 3. 例外メッセージを JSP 表示用にリクエスト属性 MESSAGE にセットします。
 * 4. IllegalStateException はアプリエラーとして、セッション属性 APP_ERROR_FORWARD_PATH (通常は表示元) にフォワードします。
 * 5. 上記以外の例外の場合は、システムエラーとしてセッション属性 SYS_ERROR_REDIRECT_URL にリダイレクト (自動フラッシュ) します。
 * 6. セッションに APP_ERROR_FORWARD_PATH も SYS_ERROR_REDIRECT_URL も無い場合は、コンテキストルートにリダイレクト (自動フラッシュ)。
 * </pre>
 * @author New Gradle Project Wizard (c) https://opensource.org/licenses/mit-license.php
 */
@Slf4j
public class AutoFlashFilter extends HttpFilter {
	
	//-------------------------------------------------------------------------
	// Servlet から使用する public static メソッド
	//-------------------------------------------------------------------------

	/** リクエスト属性名: 画面に表示するメッセージ (サーブレットから自分でセット、エラー時は例外メッセージがセットされる) */
	public static final String MESSAGE = "MESSAGE";

	/** セッション属性名: アプリエラー時のフォワード先パス (デフォルトは表示元、変更する場合はサーブレットでセット) */
	public static final String APP_ERROR_FORWARD_PATH = "APP_ERROR_FORWARD_PATH";
	
	/** セッション属性名: システムエラー時のリダイレクト先 URL (デフォルトは最後のリダイレクト先、変更する場合はサーブレットでセット) */
	public static final String SYS_ERROR_REDIRECT_URL = "SYS_ERROR_REDIRECT_URL";
	
	/**
	 * JSP にフォワードします。
	 * <pre>
	 * 標準の req.getRequestDispatcher(path).forward(req, res) の代わりに使用します。
	 * JSP 以外へのフォワードは標準の forward を使用してください。このメソッドは、以下の処理を行います。
	 * 
	 * 1. フォワードします。以下の 2 種類の記述が可能です。
	 *  ・引数が絶対パス (先頭がスラッシュ　　) の場合: /WEB-INF/jsp + 引数 (分かりやすい)
	 *  ・引数が相対パス (先頭がスラッシュ以外) の場合: /WEB-INF/jsp + getServletPath + 引数 (短い記述)
	 * 
	 *    getServletPath  引数の jspPath     RequestDispatcher#forward に渡されるパス
	 *    "/item/abc"     /item/list.jsp    /WEB-INF/jsp/item/list.jsp
	 *    "/item"         /item/list.jsp    /WEB-INF/jsp/item/list.jsp
	 *    "/item/abc"     /index.jsp        /WEB-INF/jsp/index.jsp
	 *    "/item/abc"     list.jsp          /WEB-INF/jsp/item/list.jsp
	 *    "/item"         list.jsp          /WEB-INF/jsp/list.jsp
	 *    "/"             list.jsp          /WEB-INF/jsp/list.jsp
	 *    ""              list.jsp          /WEB-INF/jsp/list.jsp
	 *    "/item/abc"     ../index.jsp      /WEB-INF/jsp/index.jsp
	 *    "/item/abc"     ../other/a.jsp    /WEB-INF/jsp/other/a.jsp
	 * 
	 * 2. AutoCsrfFilter を使用している場合は、meta と form input hidden に name="_csrf" として CSRF トークンが埋め込み。
	 * 3. フォワード先パスをセッション属性 APP_ERROR_FORWARD_PATH に保存 (アプリエラー時の自動フォワード先として使用)。
	 * 4. 後続処理を飛ばすために、正常にレスポンスがコミットされたことを示す定数 SUCCESS_RESPONSE_COMMITTED をスロー。
	 * </pre>
	 * @param jspPath JSP パス
	 */
	@SneakyThrows
	public static void forward(String jspPath) {
		RequestContext context = requestContextThreadLocal.get();
		HttpServletRequest req = context.req;
		String path = jspPath.startsWith("/") ? jspPath : req.getServletPath().replaceFirst("[^/]*$", "") + jspPath;
		path = ("/WEB-INF/jsp/" + path).replace("//", "/");
		log.debug("フォワード {} (servletPath[{}] 引数[{}])", path, req.getServletPath(), jspPath);
		req.getRequestDispatcher(path).forward(context.req, context.res);
		req.getSession().setAttribute(APP_ERROR_FORWARD_PATH, path);
		throw SUCCESS_RESPONSE_COMMITTED;
	}
	
	/**
	 * リダイレクトします。
	 * <pre>
	 * 標準の res.sendRedirect(url) の代わりに使用します。
	 * デフォルトでは、このフィルター以降 (Servlet) で追加したリクエスト属性が、リダイレクト先のリクエスト属性に転送されます。
	 * リダイレクト先に転送したくない項目がある場合は、このメソッドを呼び出す前に req#removeAttribute で個別に削除してください。
	 * 一切転送したくない場合や外部サイトへのリダイレクトは、標準の sendRedirect を使用してください。
	 * このメソッドは、以下の処理を行います。
	 * 
	 * 1. 指定した redirectUrl (null の場合はコンテキストルート) にリダイレクトします。
	 * 2. リダイレクト先 URL をセッション属性 SYS_ERROR_REDIRECT_URL に保存します (システムエラー時の自動リダイレクト先として使用)。
	 * 3. Servlet で追加されたリクエスト属性をフラッシュ属性としてセッションに保存します (リダイレクト後にリクエスト属性に復元)。
	 * 4. 後続処理を飛ばすために、正常にレスポンスがコミットされたことを示す定数 SUCCESS_RESPONSE_COMMITTED をスローします。
	 * </pre>
	 * @param redirectUrl リダイレクト先 URL
	 */
	@SneakyThrows
	public static void redirect(String redirectUrl) {
		RequestContext context = requestContextThreadLocal.get();
		String url = Objects.toString(redirectUrl, context.req.getContextPath());
		log.debug("リダイレクト {} (引数[{}])", url, redirectUrl);
		context.res.sendRedirect(url);
		HttpSession session = context.req.getSession();
		session.setAttribute(SYS_ERROR_REDIRECT_URL, url);
		session.setAttribute(FLASH, context.req.getAttribute(FLASH));
		throw SUCCESS_RESPONSE_COMMITTED;
	}
	
	/**
	 * REST API の戻り値をクライアントに返却します。
	 * <pre>
	 * 1. 引数の型が CharSequence の場合は文字列、それ以外の場合は json 文字列に変換し、レスポンスに書き込みます。
	 * 2. 後続処理を飛ばすために、正常にレスポンスがコミットされたことを示す定数 SUCCESS_RESPONSE_COMMITTED をスローします。
	 * </pre>
	 * @param resObject 返却する Java オブジェクト
	 */
	@SneakyThrows
	public static void returns(Object resObject) {
		HttpServletResponse res = requestContextThreadLocal.get().res;
		if (!(resObject instanceof CharSequence)) {
			res.setContentType("application/json");
			resObject = jsonMapper.writeValueAsString(resObject);
		}
		res.getWriter().print(resObject);
		log.debug("戻り値 {}", resObject);
		throw SUCCESS_RESPONSE_COMMITTED;
	}
	
	/**
	 * アプリエラーをスローするためのショートカットメソッドです。<br>
	 * 指定した条件が false の場合は、アプリエラーを表す IllegalStateException をスローします。
	 * @param isValid 入力チェックなどが正しい場合に true となる条件
	 * @param message 例外にセットするメッセージ (クライアントに返すメッセージ)
	 * @param args メッセージの %s や %d に String#format で埋め込む文字列
	 */
	public static void valid(boolean isValid, String message, Object... args) {
		if (!isValid) {
			throw new  IllegalStateException(String.format(message, args));
		}
	}
	
	/**
	 * JSP EL の ${name} のようにリクエスト、セッション、アプリケーションスコープから、最初に見つかった属性値を取得します。
	 * @param <T> 戻り値の型 (代入先があればキャスト不要)
	 * @param name 属性名
	 * @return 属性値。見つからない場合は null。
	 */
	@SuppressWarnings("unchecked")
	public static <T> T $(String name) {
		return (T) ServletUtil.attribute(requestContextThreadLocal.get().req, name);
	}
	
	/**
	 * JSP EL の ${name} のようにリクエスト、セッション、アプリケーションスコープから、最初に見つかった属性値を取得します。
	 * @param <T> 戻り値の型 (代入先があればキャスト不要)
	 * @param name 属性名
	 * @param defaultValue 値が見つからなかった場合のデフォルト値
	 * @return 属性値。見つからない場合は defaultValue。
	 */
	public static <T> T $(String name, T defaultValue) {
		return ObjectUtils.defaultIfNull($(name), defaultValue);
	}
	
	//-------------------------------------------------------------------------
	// Servlet フィルター処理
	//-------------------------------------------------------------------------
	
	protected static final String FLASH = "FLASH";
	protected static class SuccessResponseCommittedException extends RuntimeException {};
	protected static final RuntimeException SUCCESS_RESPONSE_COMMITTED = new SuccessResponseCommittedException();
	protected static final ObjectMapper jsonMapper = new ObjectMapper();
	protected static final ThreadLocal<RequestContext> requestContextThreadLocal = new ThreadLocal<>();
	
	/** ThreadLocal に保存するリクエストコンテキストレコード */
	protected record RequestContext (
		HttpServletRequest req,
		HttpServletResponse res
	) {};
	
	@Override @SneakyThrows
	protected void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain) {
		
		// css js などを除外 (html は web.xml で jsp 扱いのため除外しない)
		String uri = req.getRequestURI();
		if (uri.contains(".") && !uri.endsWith(".html")) {
			super.doFilter(req, res, chain); 
			return;
		}
		
		// リクエストのスレッドローカル設定と次のフィルター呼び出し
		StopWatch stopWatch = StopWatch.createStarted();
		try {
			requestContextThreadLocal.set(new RequestContext(req, res));
			
			// フラッシュをセッションから復元し、セッションから削除
			Map<String, Object> sessionFlash = $(FLASH, Collections.emptyMap());
			sessionFlash.forEach(req::setAttribute);
			req.getSession().removeAttribute(FLASH);
			
			// リクエスト属性追加時に、一時フラッシュに追加するリクエストラッパー作成 (redirect でセッションに移動)
			Map<String, Object> tempFlash = new HashMap<>();
			req.setAttribute(FLASH, tempFlash);
			HttpServletRequest flashReqWrapper = new HttpServletRequestWrapper(req) {
				public void setAttribute(String name, Object o) {
					super.setAttribute(name, o);
					tempFlash.put(name, o);
				}
				public void removeAttribute(String name) {
					super.removeAttribute(name);
					tempFlash.remove(name);
				}
			};
			super.doFilter(flashReqWrapper, res, chain);
			
		// ルート例外の getMessage() をリクエスト属性 MESSAGE にセットして jsp や html から参照できるようにする
		} catch (Throwable e) {
			if (e == SUCCESS_RESPONSE_COMMITTED) {
				return;
			}
			Throwable cause = ExceptionUtils.getRootCause(e);
			req.setAttribute(MESSAGE, req.isSecure() ? "システムに問題が発生しました。" : cause.getMessage());
			if (isAjax(req)) {
				res.getWriter().print((String) $(MESSAGE));
			} else {
				
				// アプリエラー (画面入力チェックエラーなど)
				String forwardPath = $(APP_ERROR_FORWARD_PATH);
				if (cause instanceof IllegalStateException && forwardPath != null) {
					req.getRequestDispatcher(forwardPath).forward(req, res);
					
				// システムエラー (DB 接続エラーなど)
				} else {
					String redirectUrl = $(SYS_ERROR_REDIRECT_URL);
					if (redirectUrl == null || redirectUrl.equals(req.getRequestURI())) {
						redirectUrl = req.getContextPath();
					}
					res.sendRedirect(redirectUrl);
					req.getSession().setAttribute(FLASH, Map.of(MESSAGE, $(MESSAGE)));
					log.warn(cause.getMessage(), cause);
				}
			}
			
		} finally {
			String fullUrl = DispatcherUtil.getFullUrl(req);
			log.debug("処理時間 {}ms [{}] {} {}", stopWatch.getTime(), req.getMethod(), fullUrl, $(MESSAGE, ""));
			requestContextThreadLocal.remove();
		}
	}

	/**
	 * @return AJAX リクエストの場合は true
	 */
	protected boolean isAjax(HttpServletRequest req) {
		return "XMLHttpRequest".equals(req.getHeader("X-Requested-With")) ||
				StringUtils.contains(req.getHeader("Accept"), "/json");
	}
}
