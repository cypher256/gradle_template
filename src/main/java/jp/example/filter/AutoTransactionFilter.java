package jp.example.filter;

import static com.pivovarit.function.ThrowingConsumer.*;
import static java.util.Collections.*;

import java.sql.DriverManager;
import java.util.List;

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
 * データベーストランザクションの一般的なテンプレート実装です。
 * このフィルターでは uroboroSQL を使用して、データベースの初期データロード、トランザクションを制御します。
 * </pre>
 * @author New Gradle Project Wizard (c) https://opensource.org/licenses/mit-license.php
 */
public class AutoTransactionFilter extends HttpFilter {
	
	//-------------------------------------------------------------------------
	// アプリから使用する public static メソッド
	//-------------------------------------------------------------------------

	/**
	 * 汎用 DAO トランザクションマネージャーを取得します。
	 * <pre>
	 * 自動採番の主キーを持つテーブルは、id などのエンティティに関するアノテーションは不要です。
	 * スネークケース、キャメルケースは自動変換されます。ただし、バインドパラメータ名は変換されません。
	 * <a href="https://future-architect.github.io/uroborosql-doc/why_uroborosql/"
	 * >GitHub: uroboroSQL (ウロボロスキュール)</a>
	 * </pre>
	 * @return SqlAgent
	 */
	public static SqlAgent dao() {
		return daoThreadLocal.get();
	}
	
	//-------------------------------------------------------------------------
	// Servlet フィルター処理
	//-------------------------------------------------------------------------
	
	protected static final ThreadLocal<SqlAgent> daoThreadLocal = new ThreadLocal<>();
	protected SqlConfig daoConfig;
	protected HikariDataSource dataSource;

	/** データベース接続設定と初期データロード */
	@Override @SneakyThrows
	public void init() {
		dataSource = new HikariDataSource(new HikariConfig("/database.properties"));
		daoConfig = UroboroSQL.builder(dataSource).build();
		try (SqlAgent dao = daoConfig.agent()) {
			dao.update("create_table").count(); // ファイル実行 src/main/resources/sql/create_table.sql
		}
	}
	
	/** データベースリソース破棄 */
	@Override @SneakyThrows
	public void destroy() {
		dataSource.close();
		list(DriverManager.getDrivers()).forEach(sneaky(DriverManager::deregisterDriver)); // Tomcat 警告抑止
	}
	
	/** トランザクション開始、コミット、ロールバック */
	@Override @SneakyThrows
	protected void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain) {
		
		// html css js などを除外
		if (req.getRequestURI().contains(".")) {
			super.doFilter(req, res, chain);
			return;
		}
		
		// トランザクション制御ブロック
		try (SqlAgent dao = daoConfig.agent()) {
			try {
				daoThreadLocal.set(dao);
				super.doFilter(req, res, chain); // Servlet 呼び出し
				dao.commit();
				
			} catch (Throwable e) {
				List<?> eClassList = (List<?>) getServletContext().getAttribute("NO_ROLLBACK_EXCEPTION_CLASS_LIST");
				if (eClassList != null && eClassList.contains(e.getClass())) {
					dao.commit(); // 例外でもコミットする例外クラス (Spring の noRollbackFor と同様の機能)
				} else {
					dao.rollback();
				}
				throw e;
				
			} finally {
				daoThreadLocal.remove();
			}
		}
	}
}
