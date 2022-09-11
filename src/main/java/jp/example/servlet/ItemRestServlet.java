package jp.example.servlet;

import static jp.example.filter.AutoFlashFilter.*;
import static jp.example.filter.AutoTransactionFilter.*;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import jp.example.dto.Item;
import lombok.Data;

/**
 * REST API Servlet サンプルクラスです。
 * <pre>
 * キーイベントによる AJAX リアルタイム通信のサンプルです。
 * AJAX の場合、IllegalStateException をスローすると、例外メッセージ文字列がレスポンスとして返却されます。
 * </pre>
 * @author New Gradle Project Wizard (c) https://opensource.org/licenses/mit-license.php
 */
@WebServlet("/item/api")
public class ItemRestServlet extends HttpServlet {
	
	/** 
	 * 検索画面の検索文字列 onkeyup 時の検索結果件数取得 API です。<br>
	 * 戻り値: json 結果件数情報 (例外発生時は text エラーメッセージ文字列)
	 */
	protected void doGet(HttpServletRequest req, HttpServletResponse res) {
		
		String sql = """
				SELECT COUNT(*) FROM item
				WHERE 1 = 1
					/*IF SF.isNotBlank(name)*/
						AND name LIKE /*SF.contains(name)*/'Pro' escape /*#ESC_CHAR*/'$'
					/*END*/
					/*IF SF.isNotBlank(releaseDate)*/
						AND release_date = /*releaseDate*/'2022-09-11'
					/*END*/
			""";
		
		@Data
		class SearchResult {
			Item condition = new Item(req); // クライアントで件数以外は使用しないが json 返却例としてセット
			int count = dao().queryWith(sql).paramBean(condition).one(int.class);
		}
		returns(new SearchResult());
	}
	
	/**
	 * 登録、変更画面の onkeyup、onchange 時の入力チェック API です。<br>
	 * (更新系ではないが post。post のためトークンもチェックされる。) 
	 * 戻り値: text エラーメッセージ文字列 (エラーが無い場合は戻り値なし)
	 */
	protected void doPost(HttpServletRequest req, HttpServletResponse res) {
		new Item(req).validate();
	}
}
