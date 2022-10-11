package jp.example.filter;

import java.util.Objects;
import java.util.function.Supplier;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;

import jodd.servlet.ServletUtil;
import lombok.SneakyThrows;

/**
 * リクエストコンテキストフィルターです。
 * <pre>
 * 各フィルターは基本的に独立していますが、このフィルターは唯一、他のフィルターから参照されています。
 * </pre>
 * @author New Gradle Project Wizard (c) Pleiades MIT
 */
public class RequestContextFilter extends HttpFilter {
	
	//-------------------------------------------------------------------------
	// Servlet から使用する public static メソッド
	//-------------------------------------------------------------------------
	
	/**
	 * JSP EL の ${name} のようにリクエスト、セッション、アプリケーションスコープから、最初に見つかった属性値を取得します。
	 * @param <T> 戻り値の型 (代入先があればキャスト不要)
	 * @param name 属性名
	 * @return 属性値。見つからない場合は null。
	 */
	@SuppressWarnings("unchecked")
	public static <T> T $(String name) {
		return (T) ServletUtil.attribute(request(), name);
	}
	
	/**
	 * JSP EL の ${name} のようにリクエスト、セッション、アプリケーションスコープから、最初に見つかった属性値を取得します。
	 * @param <T> 戻り値の型 (代入先があればキャスト不要)
	 * @param name 属性名
	 * @param defaultValue 値が見つからなかった場合のデフォルト値
	 * @return 属性値。見つからない場合は defaultValue。
	 */
	public static <T> T $(String name, T defaultValue) {
		return Objects.requireNonNullElse($(name), defaultValue);
	}
	
	/**
	 * JSP EL の ${name} のようにリクエスト、セッション、アプリケーションスコープから、最初に見つかった属性値を取得します。
	 * @param <T> 戻り値の型 (代入先があればキャスト不要)
	 * @param name 属性名
	 * @param defaultValueSupplier 値が見つからなかった場合のデフォルト値を生成する Supplier
	 * @return 属性値。見つからない場合は defaultValueSupplier から取得。
	 */
	public static <T> T $(String name, Supplier<T> defaultValueSupplier) {
		return Objects.requireNonNullElseGet($(name), defaultValueSupplier);
	}
	
	//-------------------------------------------------------------------------
	// Filter から使用する static メソッド
	//-------------------------------------------------------------------------
	
	static HttpServletRequest request() {
		return contextThreadLocal.get().req;
	}
	
	static HttpServletResponse response() {
		return contextThreadLocal.get().res;
	}
	
	static void set(HttpServletRequest req, HttpServletResponse res) {
		contextThreadLocal.set(new RequestContext(req, res));
	}
	
	static boolean isAjax(HttpServletRequest req) {
		return "XMLHttpRequest".equals(req.getHeader("X-Requested-With")) || // jQuery、prototype
				StringUtils.contains(req.getHeader("Accept"), "/json"); // axios
	}
	
	//-------------------------------------------------------------------------
	// Servlet フィルター処理
	//-------------------------------------------------------------------------

	private static final ThreadLocal<RequestContext> contextThreadLocal = new ThreadLocal<>();
	
	private record RequestContext (
		HttpServletRequest req,
		HttpServletResponse res
	) {};

	@Override @SneakyThrows
	protected void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain) {
		if (req.getRequestURI().contains(".")) { // css js など
			super.doFilter(req, res, chain); 
			return;
		}
		try {
			contextThreadLocal.set(new RequestContext(req, res));
			super.doFilter(req, res, chain);
		} finally {
			contextThreadLocal.remove();
		}
	}
}
