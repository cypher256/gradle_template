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

/**
 * 自動 CSRF フィルターです。
 * <pre>
 * web.xml の dispatcher 要素に REQUEST, FORWARD を指定する必要があります。
 * タグなどの指定無しで、jsp だけでなく html にも hidden、meta にトークンを自動埋め込み。Cookie にもセットされます。
 * 画面遷移ごと (AJAX アクセス除く) にトークンが新しく生成されるため、同期トークンとして使用できます。
 * </pre>
 * クライアントでのトークン手動操作
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
 * @author New Gradle Project Wizard (c) https://opensource.org/licenses/mit-license.php
 */
public class AutoCsrfFilter extends HttpFilter {

	/** CSRF トークンのセッション、Cookie、リクエストパラメーターの name */
	protected static final String _csrf = "_csrf";

	@Override @SneakyThrows
	protected void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain) {
		
		// css js などを除外 (html や jsp は除外しない)
		String uri = req.getRequestURI();
		boolean isHtml = uri.matches(".+\\.(jsp.?|html)"); // .jsp は FORWARD 時
		if (!isHtml && uri.contains(".")) {
			super.doFilter(req, res, chain);
			return;
		}
		
		// POST サブミット時のトークンチェック (REQUEST) : Servlet を介さない html へのサブミット時も対象
		if (req.getDispatcherType() == DispatcherType.REQUEST && notMatchPostToken(req, res)) {
			if (isAjax(req)) {
				res.sendError(HttpServletResponse.SC_FORBIDDEN);
			} else {
				// トップへリダイレクト (AutoFlashFilter で使えるフラッシュ属性 MESSAGE をセットしておく)
				req.getSession().setAttribute("FLASH_ATTRIBUTE", Map.of("MESSAGE", "セッションが切れました。"));
				res.sendRedirect(req.getContextPath());
				// 同期トークンチェックとしても機能する (ただし、意図的に bfcache 無効化により、戻るボタンからの送信は正常に機能する) ため、
				// 二重送信やリロード多重送信も検出できるが、事後検出ではユーザビリティが悪いため、事前に
				// JavaScript でのボタン二度押し防止、F5 POST を防ぐ PRG パターンを推奨。
			}
			return;
		}
		
		// トークン埋め込み (REQUEST, FORWARD) : html 直接アクセス、Servlet からの jsp フォワード
		if (isHtml) {
			ByteArrayResponseWrapper resWrapper = new ByteArrayResponseWrapper(res);
			super.doFilter(req, resWrapper, chain);
			String csrfToken = (String) req.getSession().getAttribute(_csrf);
			String html = new String(resWrapper.toByteArray(), resWrapper.getCharacterEncoding())
				.replaceFirst("(?i)(<head>)", format("""
					$1\n<meta name="_csrf" content="%s">""", csrfToken))
				.replaceAll("(?is)([ \t]*)(<form[^>]+post[^>]+>)", format("""
					$1$2\n$1\t<input type="hidden" name="_csrf" value="%s">""", csrfToken));
			res.setContentLength(html.getBytes(resWrapper.getCharacterEncoding()).length);
			res.getWriter().print(html);
		} else {
			super.doFilter(req, res, chain);
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
		if (!isAjax(req) || session.getAttribute(_csrf) == null) {
			session.setAttribute(_csrf, UUID.randomUUID().toString());
		}
		
		// AJAX 参照用 Cookie 書き込み
		// * Secure: isSecure で https 判定。リバースプロキシの場合は RemoteIpFilter で連携。
		// * SameSite: Strict (送信は同一サイトのみ)。ブラウザのデフォルトは Lax (別サイトから GET 可能、POST 不可)。
		// * HttpOnly: 指定なし。JavaScript から参照可能にするために指定しない。
		res.addHeader("Set-Cookie", format("XSRF-TOKEN=%s;%sSameSite=Strict;", 
				session.getAttribute(_csrf), req.isSecure() ? " Secure;" : ""));
		
		// Cache-Control で bfcache 無効化
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
