package jp.example.servlet;

import static jp.example.filter.AutoFlashFilter.*;
import static jp.example.filter.AutoTransactionFilter.*;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import jodd.servlet.DispatcherUtil;
import jp.example.entity.Item;
import jp.example.form.ItemForm;
import lombok.extern.slf4j.Slf4j;

/**
 * Servlet JSP CRUD Servlet 定義クラスです。
 * <pre>
 * 検索一覧、登録、修正、削除画面のシンプルな Servlet パターンサンプル。
 * IllegalStateException をスローすると、アプリエラーとして表示中のページにフォワードされます。
 * 以下のフィルタークラスの static メソッドを static インポート (Ctrl/Cmd + Shift + m) して使用できます。
 * forward、redirect、returns は条件分岐で呼び分ける場合でも、Servlet 内の処理はそこで終了するため、return 不要です。
 * 
 * AutoFlashFilter
 *  
 *   forward(jsp)   フォワードのショートカット (入力エラー時の戻り先として保存、CSRF 兼同期トークン自動埋め込み)
 *   redirect(url)  リダイレクトのショートカット (自動フラッシュにより、リダイレクト先でリクエスト属性がそのまま使用可能)
 *   returns(obj)   REST API などの戻り値として Java オブジェクトを JSON 文字列などに変換してクライアントに返却
 *   valid(〜)      条件とエラーメッセージを指定して、pplicationException をスローするためのショートカットメソッド
 *   $("name")      JSP EL のようにリクエスト、セッションなどのスコープから、最初に見つかった属性値を取得 (キャスト不要)
 * 
 * AutoTransactionFilter
 *  
 *   dao()          汎用 DAO トランザクションマネージャー取得 (正常時は自動コミット、ロールバックしたい場合は例外スロー)
 *   
 * </pre>
 * @author Pleiades New Gradle Project Wizard (c) MPL
 */
@Slf4j
public class ItemCrudServlet {

	/** CRUD の R: Read 検索 Servlet */
	@WebServlet("/item/list")
	public static class ListServlet extends HttpServlet {
		
		/** 検索一覧画面の表示 */
		protected void doGet(HttpServletRequest req, HttpServletResponse res) {
			
			 // 検索 SQL (2WaySQL OGNL)
			 // https://future-architect.github.io/uroborosql-doc/background/#条件分岐-if-elif-else-end
			String sql = """
					SELECT item.*, company.company_name 
					FROM item
					LEFT JOIN company ON item.company_id = company.id
					WHERE 1 = 1
						/*IF SF.isNotBlank(name)*/
							AND name LIKE /*SF.contains(name)*/'Pro' escape /*#ESC_CHAR*/'$'
						/*END*/
						/*IF SF.isNotBlank(releaseDate)*/
							AND release_date = /*releaseDate*/'2022-09-11'
						/*END*/
				""";
			
			log.debug("検索して list.jsp にフォワード");
			req.setAttribute("formList", dao().queryWith(sql).paramBean(new ItemForm(req)).collect(ItemForm.class));
			req.getSession().setAttribute("lastQueryUrl", DispatcherUtil.getFullUrl(req));
			forward("list.jsp");
		}
	}
	
	/** CRUD の C: Create 登録 Servlet */
	@WebServlet("/item/create")
	public static class CreateServlet extends HttpServlet {
		
		/** 一覧画面の新規登録ボタン → 登録画面の表示 */
		protected void doGet(HttpServletRequest req, HttpServletResponse res) {
			req.setAttribute("form", new ItemForm());
			forward("detail.jsp");
		}
		
		/** 登録画面の登録ボタン → 一覧画面へリダイレクト (PRG パターン: リロードによる二重登録抑止) */
		protected void doPost(HttpServletRequest req, HttpServletResponse res) {
			ItemForm form = new ItemForm(req).validate(req);
			dao().query(Item.class).equal("name", form.name).exists(() -> {
				throw new IllegalStateException("指定された製品名は、すでに登録されています。");
			});
			dao().insert(form.toEntity());
			req.setAttribute(MESSAGE, "登録しました。");
			redirect($("lastQueryUrl"));
		}
	}

	/** CRUD の U: Update 変更 Servlet */
	@WebServlet("/item/update")
	public static class UpdateServlet extends HttpServlet {
		
		/** 一覧画面の変更ボタン → 変更画面の表示 */
		protected void doGet(HttpServletRequest req, HttpServletResponse res) {
			Item entity = dao().find(Item.class, new ItemForm(req).id).orElseThrow(() -> new Error("存在しません。"));
			req.setAttribute("form", new ItemForm(entity));
			forward("detail.jsp");
		}
		
		/** 変更画面の更新ボタン → 一覧画面へリダイレクト (PRG パターン: リロードによる二回更新抑止) */
		protected void doPost(HttpServletRequest req, HttpServletResponse res) {
			ItemForm form = new ItemForm(req).validate(req);
			dao().query(Item.class).notEqual("id", form.id).equal("name", form.name).exists(() -> {
				throw new IllegalStateException("指定された製品名は、別の製品で使用されています。");
			});
			dao().update(form.toEntity());
			req.setAttribute(MESSAGE, "更新しました。");
			redirect($("lastQueryUrl"));
		}
	}

	/** CRUD の D: Delete 削除 Servlet */
	@WebServlet("/item/delete")
	public static class DeleteServlet extends HttpServlet {
		
		/** 一覧画面の削除ボタン → 一覧画面へリダイレクト (リロードによる二回削除抑止) */
		protected void doGet(HttpServletRequest req, HttpServletResponse res) {
			dao().delete(new ItemForm(req).toEntity());
			req.setAttribute(MESSAGE, "削除しました。");
			redirect($("lastQueryUrl"));
		}
	}
}
