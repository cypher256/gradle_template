package jp.example.filter;

import static java.util.stream.Collectors.*;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Stopwatch;

import jodd.servlet.DispatcherUtil;
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
	 * 4. 後続処理を飛ばすために、正常なレスポンスコミット済みを示す定数 SUCCESS_RESPONSE_COMMITTED をスローします。
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
		throw SUCCESS_RESPONSE_COMMITTED;
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
	 * 4. 後続処理を飛ばすために、正常なレスポンスコミット済みを示す定数 SUCCESS_RESPONSE_COMMITTED をスローします。
	 * </pre>
	 * @param redirectUrl リダイレクト先 URL
	 */
	@SneakyThrows
	public static void redirect(Object redirectUrl) {
		RequestContext context = requestContextThreadLocal.get();
		String url = Objects.toString(redirectUrl, context.req.getContextPath());
		log.debug("リダイレクト {}", url);
		context.res.sendRedirect(url);
		context.req.getSession().setAttribute(SYS_ERROR_REDIRECT_URL, url);
		context.onRedirectSaveFlash.run();
		throw SUCCESS_RESPONSE_COMMITTED;
	}
	
	/**
	 * REST API の戻り値をクライアントに返却します。
	 * <pre>
	 * 1. 引数の型が CharSequence の場合は文字列、それ以外の場合は json 文字列に変換し、レスポンスに書き込みます。
	 * 2. 後続処理を飛ばすために、正常なレスポンスコミット済みを示す定数 SUCCESS_RESPONSE_COMMITTED をスローします。
	 * </pre>
	 * @param resObject 返却オブジェクト
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
	
	protected static final RuntimeException SUCCESS_RESPONSE_COMMITTED = new RuntimeException();
	protected static final ObjectMapper jsonMapper = new ObjectMapper();
	protected static final ThreadLocal<RequestContext> requestContextThreadLocal = new ThreadLocal<>();
	
	/** ThreadLocal に保存するリクエストコンテキストレコード */
	protected record RequestContext (
		HttpServletRequest req,
		HttpServletResponse res,
		Runnable onRedirectSaveFlash
	) {};

	/** 共通エンコーディング設定 */
	@Override @SneakyThrows
	public void init() {
		ServletContext sc = getServletContext();
		sc.setRequestCharacterEncoding(StandardCharsets.UTF_8.name()); // post getParameter エンコーディング
		sc.setResponseCharacterEncoding(StandardCharsets.UTF_8.name()); // AJAX レスポンス
	}
	
	@Override @SneakyThrows
	protected void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain) {
		
		// css js などを除外 (html は web.xml で jsp 扱いのため除外しない)
		String uri = req.getRequestURI();
		if (uri.contains(".") && !uri.endsWith(".html")) {
			super.doFilter(req, res, chain); 
			return;
		}
		
		// リダイレクト先: フラッシュ属性をセッションからリクエスト属性に復元しセッションから削除
		HttpSession session = req.getSession();
		final String FLASH_ATTRIBUTE = "FLASH_ATTRIBUTE";
		@SuppressWarnings("unchecked")
		Map<String, Object> flashMap = (Map<String, Object>) session.getAttribute(FLASH_ATTRIBUTE);
		if (flashMap != null) {
			flashMap.forEach(req::setAttribute);
			session.removeAttribute(FLASH_ATTRIBUTE);
		}
		
		// リダイレクト元: redirect が呼ばれたときのリクエスト属性のセッション保存メソッド (このフィルターより前に保存されたものは除外)
		List<String> systemAttributeNames = Collections.list(req.getAttributeNames()).stream().toList();
		Runnable onRedirectSaveFlash = () -> {
			req.getSession().setAttribute(FLASH_ATTRIBUTE, Collections.list(req.getAttributeNames()).stream()
					.filter(name -> !systemAttributeNames.contains(name))
					.collect(toMap(name -> name, req::getAttribute)));
		};
		
		// リクエストのスレッドローカル設定と次のフィルター呼び出し
		Stopwatch stopwatch = Stopwatch.createStarted();
		try {
			requestContextThreadLocal.set(new RequestContext(req, res, onRedirectSaveFlash));
			super.doFilter(req, res, chain);
			
		// ルート例外の getMessage() をリクエスト属性 MESSAGE にセットして jsp や html から参照できるようにする
		} catch (Throwable e) {
			if (e == SUCCESS_RESPONSE_COMMITTED) {
				return;
			}
			Throwable cause = ExceptionUtils.getRootCause(e);
			req.setAttribute(MESSAGE, cause.getMessage());
			if (isAjax(req)) {
				res.getWriter().print(req.getAttribute(MESSAGE));
			} else {
				
				// アプリエラー (入力エラーなどの業務エラー)
				String forwardPath = (String) session.getAttribute(APP_ERROR_FORWARD_PATH);
				if (cause instanceof IllegalStateException && forwardPath != null) {
					req.getRequestDispatcher(forwardPath).forward(req, res);
					
				// システムエラー (DB エラーなど)
				} else {
					Object redirectUrl = session.getAttribute(SYS_ERROR_REDIRECT_URL);
					res.sendRedirect(Objects.toString(redirectUrl, req.getContextPath()));
					onRedirectSaveFlash.run();
					log.warn(cause.getMessage(), cause);
				}
			}
			
		} finally {
			requestContextThreadLocal.remove();
			log.debug("処理時間 {}ms [{}] {} {}", stopwatch.elapsed(TimeUnit.MILLISECONDS), req.getMethod(), 
					DispatcherUtil.getFullUrl(req), Objects.toString(req.getAttribute(MESSAGE), ""));
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
