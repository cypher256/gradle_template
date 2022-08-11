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
 * このフロントコントローラーは、有効行 150 行ほどの Servlet API の薄いラッパーとなっています。
 * <ul>
 * <li>シンプルな単層アーキテクチャー: 多層に対して、管理・保守が容易で分散が不要なトラフィックが少ないサイトに理想的なソリューション。
 * <li>自動フラッシュ属性: リダイレクト時は、リクエスト属性をセッション経由でリダイレクト先のリクエスト属性に自動転送。
 * <li>自動 CSRF トークン: Servlet でのチェック、JSP や form ベースの AJAX は対応不要。meta、input hidden 自動出力。
 * <li>自動トランザクション: DB コネクションの取得・解放の考慮不要。例外スローでロールバックし、例外にセットしたメッセージを画面に表示。
 * <li>エラー時の表示元 JSP へ自動フォワード: new IllegalArgumentException(画面メッセージ) スローで、表示元 JSP にフォワード。
 * </ul>
 * @author Pleiades All in One (License MIT: https://opensource.org/licenses/MIT)
 */
@WebFilter("/*")
@Slf4j
public class SingleTierController extends HttpFilter {
	
	//-------------------------------------------------------------------------
	// Servlet から使用するショートカット static メソッド
	//-------------------------------------------------------------------------
	
	/**
	 * DAO インスタンスを取得します。
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
	 * 以下、このメソッド以外にも適用される、例外がスローされた場合の共通動作です。
	 * <ul>
	 * <li>例外の種類に関わらずロールバックされ、例外の getMessage() がリクエスト属性にセットされます。
	 * <li>IllegalArgumentException の場合、セッション属性 FORWARD_PATH (通常は表示元) にフォワードされます。
	 * <li>上記以外の例外の場合は、セッション属性 REDIRECT_URL にリダイレクト (自動フラッシュ) されます。
	 * <li>セッションに FORWARD_PATH も REDIRECT_URL も無い場合は、コンテキストルートにリダイレクト (自動フラッシュ) されます。
	 * </ul>
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
	 * <ul>
	 * <li>JSP パース後、meta タグと form method="post" の input hidden に CSRF トークンが追加されます。
	 * <li>フォワード先パスがセッション属性 FORWARD_PATH にセットされます。
	 * <li>AJAX リクエストの場合は、リクエスト属性の MESSAGE をレスポンスに書き込んでフォワードしません。 
	 * </ul>
	 * @param jspPath JSP パス。先頭がスラッシュでない場合は "/WEB-INF/jsp/" が先頭に追加れます。
	 */
	@SneakyThrows
	public static void forward(Object jspPath) {
		if (isAjaxMessageResponse()) return;
		String path = jspPath.toString();
		if (!path.startsWith("/")) path = "/WEB-INF/jsp/" + jspPath;
		RequestContext context = requestContextThreadLocal.get();
		context.req.getSession().setAttribute(FORWARD_PATH, path);
		log.debug("[{}] {}", FORWARD_PATH, path);
		
		// JSP にフォワードして、処理後の HTML に CSRF トークン自動埋め込み (meta と form input hidden)
		// アップロード用の multipart form は未対応。JSP に action クエリー文字列 _csrf 手動追加が必要。
		// 例: <form method="post" enctype="multipart/form-data" action="/upload?_csrf=${_csrf}">
		ByteArrayResponseWrapper tempRes = new ByteArrayResponseWrapper(context.res);
		context.req.getRequestDispatcher(path).forward(context.req, tempRes);
		String csrfValue = (String) context.req.getSession().getAttribute(_csrf);
		String html = new String(tempRes.toByteArray(), tempRes.getCharacterEncoding());
		html = html.replaceFirst("(?i)(<head>)", 
			format("\n$1<meta name=\"_csrf\" content=\"%s\">", csrfValue));
		html = html.replaceAll("(?is)([ \t]*)(<form[^>]*post[^>]*>)", 
			format("$1$2\n$1\t<input type=\"hidden\" name=\"_csrf\" value=\"%s\">", csrfValue));
		context.res.getWriter().print(html);
	}
	
	/**
	 * リダイレクトします。
	 * <ul>
	 * <li>現在のリクエスト属性は、フラッシュ属性としてセッション経由でリダイレクト先に引き継がれます。
	 * <li>リダイレクト先 URL がセッション属性 REDIRECT_URL にセットされます。
	 * <li>AJAX リクエストの場合は、リクエスト属性の MESSAGE をレスポンスに書き込んでリダイレクトしません。 
	 * </ul>
	 * @param redirectUrl リダイレクト先 URL。null の場合はコンテキストルート。
	 */
	@SneakyThrows
	public static void redirect(Object redirectUrl) {
		if (isAjaxMessageResponse()) return;
		RequestContext context = requestContextThreadLocal.get();
		String url = Objects.toString(redirectUrl, context.req.getContextPath());
		context.res.sendRedirect(url);
		log.debug("[{}] {}", REDIRECT_URL, url);
		
		// リクエスト属性をフラッシュ属性としてセッションに一時保存 ("//" が含まれる場合は外部サイトとみなし除外)
		if (!url.contains("//")) {
			context.req.getSession().setAttribute(REDIRECT_URL, url);
			context.req.getSession().setAttribute(FLASH_ATTRIBUTE, 
				Collections.list(context.req.getAttributeNames()).stream()
					.collect(Collectors.toMap(name -> name, context.req::getAttribute)));
		}
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
		sc.setRequestCharacterEncoding(StandardCharsets.UTF_8.name()); // post エンコーディング (getParameter)
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
			redirect(req.getContextPath());
			return;
		}
		
		// post 時の CSRF トークンチェック (リクエスト値: パラメーター _csrf が無ければ、リクエストヘッダ X-CSRF-TOKEN)
		synchronized (this) {
			String reqCsrf = StringUtils.defaultIfBlank(req.getParameter(_csrf), req.getHeader("X-CSRF-TOKEN"));
			String sesCsrf = (String) session.getAttribute(_csrf);
			if ("POST".equals(req.getMethod()) && !StringUtils.equals(reqCsrf, sesCsrf)) {
				req.setAttribute(MESSAGE, "不正なリクエストを無視しました。");
				redirect(session.getAttribute(REDIRECT_URL));
				return;
			}
			if (!isAjax()) {
				// AJAX ではない通常の画面遷移の場合は CSRF トークンを新しくする
				session.setAttribute(_csrf, UUID.randomUUID().toString());
			}
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
					forward(forwardPath);
				} else {
					redirect(session.getAttribute(REDIRECT_URL));
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

	/** AJAX リクエストの場合は、リクエスト属性の MESSAGE をレスポンスに書き込んで true 返却 */
	@SneakyThrows
	private static boolean isAjaxMessageResponse() {
		if (isAjax()) {
			RequestContext context = requestContextThreadLocal.get();
			Object message = Objects.toString(context.req.getAttribute(MESSAGE), "処理できません。リロードしてください。");
			context.res.getWriter().print(message);
			return true;
		}
		return false;
	}

	/** AJAX リクエストの場合は true */
	private static boolean isAjax() {
		HttpServletRequest req = requestContextThreadLocal.get().req;
		return "XMLHttpRequest".equals(req.getHeader("X-Requested-With")) ||
				StringUtils.contains(req.getHeader("Accept"), "/json");
	}
}
