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
 * セッションを使用した一般的なフラッシュスコープ実装です。
 * このフィルターを使用すると、リダイレクト前のリクエスト属性がリダイレクト先で、そのまま使用できます。
 * また、例外がスローされた場合は、このフィルターで例外の種類によりフォワードまたはリダイレクトされます。
 * 以下に、Servlet で例外をスローした場合の動作を示します。
 * 
 * 1. AJAX リクエストの場合、例外メッセージ文字列をレスポンスに書き込んで終了。
 * 2. 例外メッセージを JSP 表示用にリクエスト属性 MESSAGE にセット (リダイレクトの場合は自動フラッシュ)。
 * 3. 例外の種類によりフォワードまたはリダイレクト。
 * 
 *    (1) アプリエラー (入力エラーなど): IllegalStateException とそのサブクラス → フォワード
 *    (2) アプリのシステムエラー (あるべきデータが無いなど): java.lang.Error (サブクラス除く) → リダイレクト
 *    (3) 致命的なシステムエラー (SQL 構文エラーなど): 上記以外 → スタックトレースログを出力してリダイレクト
 * 
 *    フォワード先: セッション属性 APP_ERROR_FORWARD_PATH (デフォルトは直近のフォワード先、通常は表示元)
 *    リダイレクト先: セッション属性 SYS_ERROR_REDIRECT_URL (デフォルトは直近のリダイレクト先)
 *    APP_ERROR_FORWARD_PATH も SYS_ERROR_REDIRECT_URL も無い場合は、コンテキストルートにリダイレクト。
 * 
 * 4. 例外が再スローされ、上位フィルターの AutoTransactionFilter でロールバック。
 * 
 * </pre>
 * @author Pleiades New Gradle Project Wizard
 */
@Slf4j
public class AutoFlashFilter extends HttpFilter {
	
	//-------------------------------------------------------------------------
	// Servlet から使用する public static メソッド
	//-------------------------------------------------------------------------

	/** リクエスト属性名: 画面に表示するメッセージ (Servlet でセット、エラー時は例外メッセージがセットされる) */
	public static final String MESSAGE = "MESSAGE";

	/** セッション属性名: アプリエラー時のフォワード先パス (デフォルトは表示元、Servlet で変更可能) */
	public static final String APP_ERROR_FORWARD_PATH = "APP_ERROR_FORWARD_PATH";
	
	/** セッション属性名: システムエラー時のリダイレクト先 URL (デフォルトは最後のリダイレクト先、Servlet で変更可能) */
	public static final String SYS_ERROR_REDIRECT_URL = "SYS_ERROR_REDIRECT_URL";
	
	/**
	 * JSP にフォワードします。
	 * <pre>
	 * 標準の req.getRequestDispatcher(path).forward(req, res) の代わりに使用します。
	 * JSP 以外へのフォワードは標準の forward を使用してください。このメソッドは、以下の処理を行います。
	 * 
	 * 1. フォワードします。以下の 2 種類の指定が可能です。
	 *  ・引数が絶対パス (先頭がスラッシュ　　) の場合: /WEB-INF/jsp + 引数 (ルートが固定のため分かりやすい)
	 *  ・引数が相対パス (先頭がスラッシュ以外) の場合: /WEB-INF/jsp + getServletPath + 引数 (より短い記述)
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
	 * 4. 後続処理をスキップするために、正常にレスポンスがコミットされたことを示す定数 SUCCESS_RESPONSE_COMMITTED スロー。
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
		req.getRequestDispatcher(path).forward(req, context.res);
		req.getSession().setAttribute(APP_ERROR_FORWARD_PATH, path);
		throw SUCCESS_RESPONSE_COMMITTED;
	}
	
	/**
	 * リダイレクトします。
	 * <pre>
	 * 標準の res.sendRedirect(url) の代わりに使用します。
	 * 自動フラッシュ機構により、現在のリクエスト属性がリダイレクト先でも、そのままリクエスト属性として使用できます。
	 * ただし、Servlet コンテナーや上位フィルターにより設定された、システムが使用するリクエスト属性は、
	 * リダイレクト先に引き継ぐと問題が発生する可能性があるため、このフィルター以降 (通常は Servlet) でセットした
	 * リクエスト属性のみが転送されます。
	 * 
	 * 転送したくない項目がある場合は、このメソッドを呼び出す前に req#removeAttribute で個別に削除してください。
	 * 外部サイトへのリダイレクトや一切転送したくない場合は、普通の res#sendRedirect を使用してください。
	 * このメソッドは、以下の処理を行います。
	 * 
	 * 1. 指定した flashMessage をリクエスト属性 MESSAGE にセット (使用しない場合は null を指定)。
	 * 2. 指定した redirectUrl (null の場合はコンテキストルート) をリダイレクト先としてレスポンスにセット。
	 * 3. リダイレクト先 URL をセッション属性 SYS_ERROR_REDIRECT_URL に保存 (システムエラー時の自動リダイレクト先)。
	 * 4. このフィルター以降で追加されたリクエスト属性をフラッシュ属性としてセッションに保存 (リダイレクト先で復元)。
	 * 5. 後続処理をスキップするために、正常にレスポンスがコミットされたことを示す定数 SUCCESS_RESPONSE_COMMITTED スロー。
	 * </pre>
	 * @param redirectUrl リダイレクト先 URL
	 * @param flashMessage フラッシュメッセージ (使用しない場合は null)
	 */
	@SneakyThrows
	public static void redirect(String redirectUrl, String flashMessage) {
		RequestContext context = requestContextThreadLocal.get();
		HttpServletRequest req = context.req;
		req.setAttribute(MESSAGE, flashMessage);
		String url = Objects.toString(redirectUrl, req.getContextPath());
		log.debug("リダイレクト {} (引数[{}])", url, redirectUrl);
		context.res.sendRedirect(url);
		req.getSession().setAttribute(SYS_ERROR_REDIRECT_URL, url);
		req.getSession().setAttribute(FLASH, req.getAttribute(FLASH));
		throw SUCCESS_RESPONSE_COMMITTED;
	}
	
	/**
	 * REST API の戻り値をクライアントに返却します。
	 * <pre>
	 * 1. 引数の型が CharSequence の場合は文字列、それ以外の場合は json 文字列に変換し、レスポンスに書き込み。
	 * 2. 後続処理をスキップするために、正常にレスポンスがコミットされたことを示す定数 SUCCESS_RESPONSE_COMMITTED スロー。
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
	protected static final RuntimeException SUCCESS_RESPONSE_COMMITTED = new RuntimeException();
	protected static final ObjectMapper jsonMapper = new ObjectMapper();
	protected static final ThreadLocal<RequestContext> requestContextThreadLocal = new ThreadLocal<>();
	
	/** ThreadLocal に保存するリクエストコンテキストレコード */
	protected record RequestContext (
		HttpServletRequest req,
		HttpServletResponse res
	) {};
	
	@Override @SneakyThrows
	protected void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain) {
		
		// css js などを除外 (html は web.xml で jsp 扱いのため除外しない、ドットを含む Servlet マッピングはサポートしない)
		String uri = req.getRequestURI();
		if (uri.contains(".") && !uri.endsWith(".html")) {
			super.doFilter(req, res, chain); 
			return;
		}
		
		// リクエストのスレッドローカル設定と次のフィルター呼び出し
		StopWatch stopWatch = StopWatch.createStarted();
		try {
			requestContextThreadLocal.set(new RequestContext(req, res));
			
			// フラッシュは画面の機能のため AJAX の場合はフラッシュを復元・削除しない (例外ハンドリングは必要)
			if (isAjax(req)) {
				super.doFilter(req, res, chain); // AJAX Servlet 呼び出し
				return;
			}
			
			// フラッシュをセッションから復元し、セッションから削除
			Map<String, Object> sessionFlash = $(FLASH, Collections.emptyMap());
			sessionFlash.forEach(req::setAttribute);
			req.getSession().removeAttribute(FLASH);
			
			// リクエスト属性追加時に、一時フラッシュに追加するリクエストラッパー作成 (redirect 時にセッションに移動)
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
			requestContextThreadLocal.set(new RequestContext(flashReqWrapper, res));
			super.doFilter(flashReqWrapper, res, chain); // 画面 Servlet 呼び出し
			
		} catch (Throwable e) {
			if (e == SUCCESS_RESPONSE_COMMITTED) {
				return; // 正常 (このクラスのメソッド forward, redirect からの正常時のスロー)
			}
			handleException(req, res, e);
			throw e; // 再スローする (上位フィルターでロールバックなどを制御できるようにするため)
			
		} finally {
			String fullUrl = DispatcherUtil.getFullUrl(req);
			log.debug("処理時間 {}ms [{}] {} {}", stopWatch.getTime(), req.getMethod(), fullUrl, $(MESSAGE, ""));
			requestContextThreadLocal.remove();
		}
	}
	
	/** Servlet で発生したすべての例外を処理 */
	@SneakyThrows
	protected void handleException(HttpServletRequest req, HttpServletResponse res, Throwable e) {
		
		// IllegalStateException インスタンスと Error クラスはアプリエラー扱い (スタックトレースログを出さない)
		Throwable cause = ExceptionUtils.getRootCause(e);
		boolean isAppErrorFoward = cause instanceof IllegalStateException;
		boolean isAppErrorRedirect = cause.getClass() == Error.class;
		String message = "❌ " + cause.getMessage();
		if (!isAppErrorFoward && !isAppErrorRedirect) {
			log.warn("致命的システムエラー", cause);
			if (req.isSecure()) {
				message = "❌ システムに問題が発生しました。";
			}
		}
		req.setAttribute(MESSAGE, message); // jsp や html から参照可能にする (finally でも使用)
		
		// AJAX リクエスト時のエラー (アプリエラー、システムエラー両方) → メッセージ文字列を返す
		if (isAjax(req)) {
			res.getWriter().print(message);
			return;
		}
		
		// アプリエラー (IllegalStateException 画面入力チェックエラーなど、アプリ側で入力画面に戻したい場合)
		// → 入力画面にフォワード
		String forwardPath = $(APP_ERROR_FORWARD_PATH);
		if (isAppErrorFoward && forwardPath != null) {
			req.getRequestDispatcher(forwardPath).forward(req, res);
			return;
		}
			
		// アプリのシステムエラー (Error.class - アプリ側で直近にリダイレクトにしたい場合)
		// 致命的なシステムエラー (SQLException など - Error との違いはスタックトレースログ有無のみ) 
		// → 直近のリダイレクト先またはトップにリダイレクト
		String redirectUrl = $(SYS_ERROR_REDIRECT_URL, req.getContextPath());
		if (redirectUrl.equals(req.getRequestURI())) {
			log.warn("リダイレクトループ検出: {} {}", req.getRequestURI(), message, cause);
			res.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			return;
		}
		res.sendRedirect(redirectUrl);
		req.getSession().setAttribute(FLASH, Map.of(MESSAGE, message));
	}

	/**
	 * @return AJAX リクエストの場合は true
	 */
	protected boolean isAjax(HttpServletRequest req) {
		return "XMLHttpRequest".equals(req.getHeader("X-Requested-With")) ||
				StringUtils.contains(req.getHeader("Accept"), "/json");
	}
}
