package jp.example.servlet;

import static jp.example.filter.AutoFlashFilter.*;
import static jp.example.filter.AutoTransactionFilter.*;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import jp.example.form.ItemForm;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * REST API Servlet サンプルクラスです。
 * <pre>
 * キーイベントによる AJAX リアルタイム通信のサンプルです。
 * AJAX の場合、IllegalStateException をスローすると、例外メッセージ文字列がレスポンスとして返却されます。
 * </pre>
 * @author Pleiades New Gradle Project Wizard
 */
@WebServlet("/item/api")
@Slf4j
public class ItemRestServlet extends HttpServlet {
	
	/** 
	 * 検索画面の検索文字列 onkeyup 時の検索結果件数取得 API です。<br>
	 * 戻り値: json 結果件数情報 (例外発生時は text エラーメッセージ文字列)
	 */
	protected void doGet(HttpServletRequest req, HttpServletResponse res) {
		
		String sql = """
				SELECT COUNT(*) 
				FROM item
				WHERE 1 = 1
					/*IF SF.isNotBlank(name)*/
						AND name LIKE /*SF.contains(name)*/'Pro' escape /*#ESC_CHAR*/'$'
					/*END*/
					/*IF SF.isNotBlank(releaseDate)*/
						AND release_date = /*releaseDate*/'2022-09-11'
					/*END*/
			""";
		
		log.debug("検索結果件数情報を json として返却");
		@Data
		class SearchResult {
			ItemForm condition = new ItemForm(req); // クライアントで件数以外は使用しないが json 返却例としてセット
			int count = dao().queryWith(sql).paramBean(condition).one(int.class);
		}
		returns(new SearchResult());
	}
	
	/**
	 * 登録、変更画面の onkeyup、onchange 時の入力チェック API です。<br>
	 * (サンプルのため post。CSRF チェックされる。通常は更新系でない場合は get が望ましい。) 
	 * 戻り値: text エラーメッセージ文字列 (エラーが無い場合は戻り値なし)
	 */
	protected void doPost(HttpServletRequest req, HttpServletResponse res) {
		new ItemForm(req).validate(req);
	}
}
