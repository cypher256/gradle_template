package jp.example.filter;

import java.sql.DriverManager;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import jp.co.future.uroborosql.SqlAgent;
import jp.co.future.uroborosql.UroboroSQL;
import jp.co.future.uroborosql.config.SqlConfig;
import lombok.SneakyThrows;

/**
 * 自動トランザクションフィルターです。
 * <pre>
 * データベースコネクションの取得・解放を行います。
 * サーブレットで例外がスローされた場合はロールバックします。例外の扱いは {@link AutoControlFilter#valid} を参照してください。
 * </pre>
 * @author Pleiades All in One (License MIT: https://opensource.org/licenses/MIT)
 */
public class AutoTransactionFilter extends HttpFilter {
	
	//-------------------------------------------------------------------------
	// Servlet から使用するメソッド
	//-------------------------------------------------------------------------

	/**
	 * uroboroSQL DAO インスタンスを取得します。
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
	
	//-------------------------------------------------------------------------
	// Servlet フィルター処理
	//-------------------------------------------------------------------------
	
	private static final ThreadLocal<SqlAgent> daoThreadLocal = new ThreadLocal<>();
	private SqlConfig daoConfig;
	private HikariDataSource dataSource;

	/** データベース接続設定と初期データロード */
	@Override @SneakyThrows
	public void init() {
		dataSource = new HikariDataSource(new HikariConfig("/database.properties"));
		daoConfig = UroboroSQL.builder(dataSource).build();
		try (SqlAgent dao = daoConfig.agent()) {
			dao.update("create_table").count(); // ファイル実行 /src/main/resources/sql/create_table.sql
		}
	}
	
	/** データベースリソース破棄 */
	@Override @SneakyThrows
	public void destroy() {
		dataSource.close();
		DriverManager.deregisterDriver(DriverManager.getDrivers().nextElement()); // Tomcat 警告抑止
	}
	
	/** トランザクション開始、コミット、ロールバック */
	@Override @SneakyThrows
	protected void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain) {
		if (req.getRequestURI().contains(".")) { // css  js など
			super.doFilter(req, res, chain);
			return;
		}
		try (SqlAgent dao = daoConfig.agent()) {
			try {
				daoThreadLocal.set(dao);
				super.doFilter(req, res, chain);
				dao.commit();
			} catch (Throwable e) {
				dao.rollback();
				throw e; // 例外処理は呼び出し元のフィルターに任せる
			} finally {
				daoThreadLocal.remove();
			}
		}
	}
}
