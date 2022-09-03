package jp.example.filter;

import static com.google.common.collect.Lists.*;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import javax.servlet.FilterChain;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Stopwatch;

import jodd.servlet.DispatcherUtil;
import jodd.servlet.ServletUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * 自動フラッシュスコープとエラーを制御するフィルターです。
 * <pre>
 * リダイレクト時に使用される一般的なセッションフラッシュスコープ実装。このフィルターではデフォルトで自動制御します。
 * Servlet で設定したリダイレクト前のリクエスト属性は、リダイレクト先でもリクエスト属性として、そのまま使用できます。
 * このフィルターでは、その他の機能として、アプリエラー時に自動フォワード、システムエラー時に自動リダイレクトします。
 * </pre>
 * 以下に、Servlet で例外がスローされた場合の動作を示します。
 * <pre>
 * 1. AutoTransactionFilter を使用している場合はロールバックします。
 * 2. 例外メッセージを JSP 表示用にリクエスト属性 MESSAGE にセットします。
 * 3. IllegalStateException の場合、アプリエラーとしてセッション属性 APP_ERROR_FORWARD_PATH (通常は表示元) にフォワードします。
 * 4. 上記以外の例外の場合は、システムエラーとしてセッション属性 SYS_ERROR_REDIRECT_URL にリダイレクト (自動フラッシュ) します。
 * 5. セッションに APP_ERROR_FORWARD_PATH も SYS_ERROR_REDIRECT_URL も無い場合は、コンテキストルートにリダイレクト (自動フラッシュ)。
 * <prel>
 * @author New Gradle Project Wizard (c) https://opensource.org/licenses/mit-license.php
 */
@Slf4j
public class AutoFlashFilter extends HttpFilter {
	
	//-------------------------------------------------------------------------
	// Servlet から使用する public static メソッド
	//-------------------------------------------------------------------------

	/** リクエスト属性名: 画面に表示するメッセージ (サーブレットから自分でセット、エラー時は例外メッセージがセットされる) */
	public static final String MESSAGE = "MESSAGE";
	
	/** リクエスト属性名: リダイレクト時にフラッシュスコープとして扱う Map<String, Object> */
	public static final String FLASH = "FLASH";

	/** セッション属性名: アプリエラー時のフォワード先パス (デフォルトは表示元、変更する場合はサーブレットでセット) */
	public static final String APP_ERROR_FORWARD_PATH = "APP_ERROR_FORWARD_PATH";
	
	/** セッション属性名: システムエラー時のリダイレクト先 URL (デフォルトは最後のリダイレクト先、変更する場合はサーブレットでセット) */
	public static final String SYS_ERROR_REDIRECT_URL = "SYS_ERROR_REDIRECT_URL";
	
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
	 * 4. 後続処理を飛ばすために、正常にレスポンスがコミットされたことを示す定数 SUCCESS_RESPONSE_COMMITTED をスローします。
	 * </pre>
	 * @param jspPath JSP パス
	 */
	@SneakyThrows
	public static void forward(String jspPath) {
		RequestContext context = requestContextThreadLocal.get();
		String path = jspPath.startsWith("/") ? (String) jspPath : "/WEB-INF/jsp/" + jspPath;
		log.debug("フォワード {}", path);
		context.req.getSession().setAttribute(APP_ERROR_FORWARD_PATH, path);
		context.req.getRequestDispatcher(path).forward(context.req, context.res);
		throw SUCCESS_RESPONSE_COMMITTED;
	}
	
	/**
	 * リダイレクトします。
	 * <pre>
	 * 標準の res.sendRedirect(url) の代わりに使用します。
	 * 外部サイトへのリダイレクトは、標準の sendRedirect を使用してください。このメソッドは、以下の処理を行います。
	 * 
	 * 1. 指定した redirectUrl (null の場合はコンテキストルート) にリダイレクトします。
	 * 2. リダイレクト先 URL をセッション属性 SYS_ERROR_REDIRECT_URL に保存します (システムエラー時のリダイレクト先として使用)。
	 * 3. 現在のリクエスト属性をフラッシュ属性としてセッションに保存します (リダイレクト後にリクエスト属性に復元)。
	 * 4. 後続処理を飛ばすために、正常にレスポンスがコミットされたことを示す定数 SUCCESS_RESPONSE_COMMITTED をスローします。
	 * 
	 * リダイレクト先に引き継ぐフラッシュスコープの内容は、このメソッド呼び出し前に下記で取得して、変更することができます。
	 * 	  Map<String, Object> flash = $(FLASH);
	 * </pre>
	 * @param redirectUrl リダイレクト先 URL
	 */
	@SneakyThrows
	public static void redirect(String redirectUrl) {
		RequestContext context = requestContextThreadLocal.get();
		String url = Objects.toString(redirectUrl, context.req.getContextPath());
		log.debug("リダイレクト {}", url);
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
	
	//-------------------------------------------------------------------------
	// Servlet フィルター処理
	//-------------------------------------------------------------------------
	
	protected static class SuccessResponseCommitedException extends RuntimeException {};
	protected static final RuntimeException SUCCESS_RESPONSE_COMMITTED = new SuccessResponseCommitedException();
	protected static final ObjectMapper jsonMapper = new ObjectMapper();
	protected static final ThreadLocal<RequestContext> requestContextThreadLocal = new ThreadLocal<>();
	
	/** ThreadLocal に保存するリクエストコンテキストレコード */
	protected record RequestContext (
		HttpServletRequest req,
		HttpServletResponse res
	) {};

	/** 共通エンコーディング設定 */
	@Override @SneakyThrows
	public void init() {
		ServletContext sc = getServletContext();
		sc.setRequestCharacterEncoding(StandardCharsets.UTF_8.name()); // post getParameter エンコーディング
		sc.setResponseCharacterEncoding(StandardCharsets.UTF_8.name()); // AJAX レスポンス
		sc.setAttribute("NO_ROLLBACK_EXCEPTION_CLASS_LIST", newArrayList(SuccessResponseCommitedException.class));
	}
	
	@Override @SneakyThrows
	protected void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain) {
		
		// css js などを除外 (html は web.xml で jsp 扱いのため除外しない)
		String uri = req.getRequestURI();
		if (uri.contains(".") && !uri.endsWith(".html")) {
			super.doFilter(req, res, chain); 
			return;
		}
		
		// リクエストのスレッドローカル設定と次のフィルター呼び出し
		Stopwatch stopwatch = Stopwatch.createStarted();
		HttpSession session = req.getSession();
		try {
			requestContextThreadLocal.set(new RequestContext(req, res));
			
			// フラッシュをセッションから復元
			Map<String, Object> sessionFlash = $(FLASH, Collections.emptyMap());
			sessionFlash.forEach(req::setAttribute);
			session.removeAttribute(FLASH);
			
			// リクエスト属性追加時に、一時フラッシュに追加するリクエストラッパー作成 (redirect でセッションに移動)
			Map<String, Object> tempFlash = new HashMap<>();
			req.setAttribute(FLASH, tempFlash);
			HttpServletRequest reqFlashWrapper = new HttpServletRequestWrapper(req) {
				public void setAttribute(String name, Object o) {
					super.setAttribute(name, o);
					tempFlash.put(name, o);
				};
			};
			super.doFilter(reqFlashWrapper, res, chain);
			
		// ルート例外の getMessage() をリクエスト属性 MESSAGE にセットして jsp や html から参照できるようにする
		} catch (Throwable e) {
			if (e == SUCCESS_RESPONSE_COMMITTED) {
				return;
			}
			Throwable cause = ExceptionUtils.getRootCause(e);
			req.setAttribute(MESSAGE, cause.getMessage());
			if (isAjax(req)) {
				res.getWriter().print((String) $(MESSAGE));
			} else {
				
				// アプリエラー (入力エラーなどの業務エラー)
				String forwardPath = $(APP_ERROR_FORWARD_PATH);
				if (cause instanceof IllegalStateException && forwardPath != null) {
					req.getRequestDispatcher(forwardPath).forward(req, res);
					
				// システムエラー (DB エラーなど)
				} else {
					res.sendRedirect($(SYS_ERROR_REDIRECT_URL, req.getContextPath()));
					session.setAttribute(FLASH, Map.of(MESSAGE, $(MESSAGE)));
					log.warn(cause.getMessage(), cause);
				}
			}
			
		} finally {
			long time = stopwatch.elapsed(TimeUnit.MILLISECONDS);
			log.debug("処理時間 {}ms [{}] {} {}", time, req.getMethod(), DispatcherUtil.getFullUrl(req), $(MESSAGE, ""));
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
