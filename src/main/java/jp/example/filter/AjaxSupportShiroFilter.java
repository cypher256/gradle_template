package jp.example.filter;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.web.filter.authc.FormAuthenticationFilter;

/**
 * 認証フレームワーク Apach Shiro のフォーム認証フィルターに AJAX サポートを追加したフィルターです。
 * <pre>
 * このフィルターはセキュリティに関するもので、必須ではありません。web.xml ではなく /WEB-INF/shiro.ini に設定します。
 * Shiro は認証エラーの場合、どんなリクエストでもログイン画面にリダイレクトするため、AJAX の場合はこのフィルターが必要です。
 * web.xml で ShiroFilter が有効で、shiro.ini の urls パターンに、このフィルターが設定されている場合に使用されます。
 * </pre>
 * @author New Gradle Project Wizard (c) Pleiades MIT
 */
public class AjaxSupportShiroFilter extends FormAuthenticationFilter {

	/**
	 * 認証されていない場合の処理です。
	 * <pre>
	 * AJAX の場合: HTTP ステータス 401 を返却
	 * それ以外の場合: shiro.ini に設定したログイン画面にリダイレクト (Shiro フォーム認証のデフォルト動作)
	 * </pre>
	 * @return 処理を続行する場合は true。レスポンス設定済みの場合は false。
	 */
	@Override
	protected boolean onAccessDenied(ServletRequest req, ServletResponse res) throws Exception {
		if (isAjax((HttpServletRequest) req)) {
			((HttpServletResponse) res).sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
		} else {
			return super.onAccessDenied(req, res);
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
