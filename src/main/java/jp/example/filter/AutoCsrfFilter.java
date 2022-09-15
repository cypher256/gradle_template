package jp.example.filter;

import static java.lang.String.*;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import javax.servlet.DispatcherType;
import javax.servlet.FilterChain;
import javax.servlet.http.HttpFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.lang3.StringUtils;

import jodd.servlet.ServletUtil;
import jodd.servlet.filter.ByteArrayResponseWrapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * 自動 CSRF フィルターです。
 * <pre>
 * web.xml の dispatcher 要素に REQUEST, FORWARD を指定する必要があります。
 * 一般的な CSRF 実装と比較して以下が自動化されているため、通常は Servlet や JSP で何もする必要はありません。
 * 
 * 1. Java コードや特別なタグ指定なしで、自動的に jsp と html に meta や form の hidden を埋め込み、Cookie にもセット。
 * 2. 画面遷移ごとにトークンが新しく生成されるため、同期トークンとしても機能する。
 *    ・AJAX アクセスの場合はトークンを更新しないため、AJAX 後の画面からの post でトークンエラーは発生しない。
 *    ・画面遷移と AJAX の順序が保証されない並行リクエストがある場合の動作は不定。
 * 3. Chrome のような bfcache 無効化対応ブラウザでは、標準の戻るボタンが使用可能。同期トークン相違によるエラーは発生しない。
 *    ・修正画面 → 完了画面 → ブラウザ戻る → 修正画面 のような遷移でも直感的な再修正が可能
 * 
 * クライアントでのトークン手動操作
 * 
 * form サブミット、form 内容の JavaScript 送信、Angular、axios の場合は、自動的にトークンがリクエストに含まれます。
 * 自動 CSRF 送信をサポートしないプレーンな JavaScript などで手動 post する場合は、下記のいずれかで取得し、
 * post リクエストヘッダーの X-XSRF-TOKEN にセットする必要があります。
 * 
 *     // form の hidden から取得 (method="post" の form がある画面のみ)
 *     document.forms[0]._csrf.value
 *     // meta タグから取得する場合 (すべての画面)
 *     document.querySelector("meta[name='_csrf']").content
 *     // Cookie から取得する場合 (すべての画面)
 *     document.cookie.split('; ').find(e => e.startsWith('XSRF-TOKEN')).split('=')[1]
 * 
 * また、アップロード用の multipart post form の場合は、hidden が getParameter で取得できないため、action 属性に
 * クエリー文字列として指定する必要がありますが、今のところ、自動埋め込みには対応していません。
 * 下記のようにセッション属性値 ${_csrf} を明示的に指定してください。
 * 
 *     form タグ
 *     action="/upload?_csrf=${_csrf}" method="post" enctype="multipart/form-data"
 * 
 * </pre>
 * @author Pleiades New Gradle Project Wizard
 */
@Slf4j
public class AutoCsrfFilter extends HttpFilter {

	/** CSRF トークンのセッション、Cookie、リクエストパラメーターの name */
	protected static final String _csrf = "_csrf";

	@Override @SneakyThrows
	protected void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain) {
		
		// css js などを除外 (html や jsp は除外しない)
		String uri = req.getRequestURI();
		boolean isHtml = StringUtils.endsWithAny(uri, ".jsp", ".html"); // .jsp は FORWARD 時
		if (!isHtml && uri.contains(".")) {
			super.doFilter(req, res, chain);
			return;
		}
		
		// [REQUEST] POST サブミット時のトークンチェック (Servlet を介さない html へのサブミット時も対象)
		if (req.getDispatcherType() == DispatcherType.REQUEST && notMatchPostToken(req, res)) {
			if (isAjax(req)) {
				res.sendError(HttpServletResponse.SC_FORBIDDEN);
			} else {
				// トップへリダイレクト (AutoFlashFilter で使えるフラッシュ属性 MESSAGE をセットしておく)
				req.getSession().setAttribute("FLASH", Map.of("MESSAGE", "セッションが切れました。"));
				res.sendRedirect(req.getContextPath());
				// 同期トークンチェックとしても機能する (bfcache 無効化により、戻るボタンからの送信は正常に機能する) ため、
				// 二重送信やリロード多重送信も検出できるが、事後検出ではユーザビリティが悪いため、事前に
				// JavaScript でのボタン二度押し防止、F5 POST を防ぐ PRG パターンを推奨。
			}
			return;
		}
		
		// [REQUEST, FORWARD] トークン埋め込み (html 直接アクセス、Servlet からの jsp フォワード)
		// ここで想定する文字列パターンに一致しない場合は、JSP に ${_csrf} を指定する必要がある
		if (isHtml) {
			ByteArrayResponseWrapper resWrapper = new ByteArrayResponseWrapper(res);
			super.doFilter(req, resWrapper, chain);
			String csrfToken = (String) req.getSession().getAttribute(_csrf);
			String html = new String(resWrapper.toByteArray(), resWrapper.getCharacterEncoding())
				// meta タグ追加
				.replaceFirst("(?i)(<head>)", format("""
					$1\n<meta name="_csrf" content="%s">""", csrfToken))
				// form post 内に hidden 追加
				.replaceAll("(?is)([ \t]*)(<form[^>]+post[^>]+>)", format("""
					$1$2\n$1\t<input type="hidden" name="_csrf" value="%s">""", csrfToken));
			res.setContentLength(html.getBytes(resWrapper.getCharacterEncoding()).length);
			res.getWriter().print(html);
		} else {
			super.doFilter(req, res, chain);
		}
	}

	/**
	 * post 時のトークンをチェックします (ある程度並行リクエストに対応するため synchronized)。
	 * @return トークンが一致しない場合は true
	 */
	synchronized protected boolean notMatchPostToken(HttpServletRequest req, HttpServletResponse res) throws IOException {
		
		// トークンチェック (リクエスト、ヘッダー、Cookie は標準的な名前を使用)
		HttpSession session = req.getSession();
		if ("POST".equals(req.getMethod())) {
			String sesCsrf = (String) session.getAttribute(_csrf);
			String reqCsrf = StringUtils.firstNonEmpty(
				req.getParameter(_csrf), 		// form hidden "_csrf" → フォームサブミットやフォームベースの AJAX
				req.getHeader("X-CSRF-TOKEN"),	// meta "_csrf" → jQuery からの AJAX 送信でよく使われる名前
				req.getHeader("X-XSRF-TOKEN")	// Cookie "XSRF-TOKEN" → Angular、axios などで自動的に使用される名前
			);
			if (reqCsrf == null || !reqCsrf.equals(sesCsrf)) {
				log.debug("CSRF 不一致 (session:{} request:{})", sesCsrf, reqCsrf);
				return true; // エラー
			}
		}
		
		// 画面遷移の場合はトークンを生成し直し
		if (!isAjax(req) || session.getAttribute(_csrf) == null) {
			session.setAttribute(_csrf, UUID.randomUUID().toString());
		}
		
		// AJAX 参照用 Cookie 書き込み
		// * Secure: isSecure で判定。localhost では無視される。Apache などと連携するには RemoteIpFilter 設定が必要。
		// * SameSite: Strict (送信は同一サイトのみ)。ブラウザのデフォルトは Lax (別サイトから GET 可能、POST 不可)。
		// * HttpOnly: 指定なし。JavaScript から参照可能にするために指定しない。
		res.addHeader("Set-Cookie", format("XSRF-TOKEN=%s;%sSameSite=Strict;", 
				session.getAttribute(_csrf), req.isSecure() ? " Secure;" : ""));
		
		// Cache-Control で bfcache 無効化 (ブラウザの種類やバージョンに依存する場合あり)
		// * ブラウザ戻るボタンでできるだけエラーにならないようにする
		// * ブラウザ戻るボタンでの get ページ表示は、bfcache ではなくサーバから再取得される (トークンを一致させる)
		// * 登録 → ブラウザ戻るボタン → 登録: 不正ではなく連続登録できる
		// * 更新 → ブラウザ戻るボタン → 更新: 不正ではなく再修正できる
		// * 二重送信やリロードによる POST 多重送信はトークンエラーとなる
		// * 戻るボタン後のサブミットをエラーにしたい場合は、下記の bfcache を無効化しないようにする
		ServletUtil.preventCaching(res);
		return false; // 正常
	}

	/**
	 * @return AJAX リクエストの場合は true
	 */
	protected boolean isAjax(HttpServletRequest req) {
		return "XMLHttpRequest".equals(req.getHeader("X-Requested-With")) ||
				StringUtils.contains(req.getHeader("Accept"), "/json");
	}
}
