package jp.example.servlet;

import static jp.example.filter.AutoFlashFilter.*;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import jp.example.form.ItemForm;

/**
 * サンプルテーブル item の SPA CRUD API Servlet です。
 * <pre>
 * JSP 版と同じ機能を SPA (React や Vue などのシングルページアプリケーション) 向けの API として実装した Servlet です。
 * データを返す API のみで構成され、フロント側の React Router や Vue Router などがコントローラーとなります。
 * レスポンスに書き込み無し (returns していない) かつ例外無しの場合は、レスポンス body は空で HTTP 200 になります。
 * Servlet でスローされた例外は AutoFlashFilter で例外メッセージがレスポンスに書き込まれ HTTP 200 または 202 になります。
 * </pre>
 * @author New Gradle Project Wizard (c) Pleiades MIT
 */
public class SpaCrudServlet {

	@WebServlet("/spa/search")
	public static class SearchServlet extends HttpServlet {
		protected void doGet(HttpServletRequest req, HttpServletResponse res) {
			returns(new ItemForm(req).findFormList());
		}
	}

	@WebServlet("/spa/count")
	public static class CountServlet extends HttpServlet {
		protected void doGet(HttpServletRequest req, HttpServletResponse res) {
			returns("結果予想件数: " + new ItemForm(req).count() + " 件");
		}
	}
	
	@WebServlet("/spa/delete")
	public static class DeleteServlet extends HttpServlet {
		protected void doPost(HttpServletRequest req, HttpServletResponse res) {
			new ItemForm(req).delete();
		}
	}
	
	@WebServlet("/spa/select")
	public static class SelectServlet extends HttpServlet {
		protected void doGet(HttpServletRequest req, HttpServletResponse res) {
			returns(new ItemForm(req).findFormById());
		}
	}
	
	@WebServlet("/spa/selectCompany")
	public static class SelectCompanyServlet extends HttpServlet {
		protected void doGet(HttpServletRequest req, HttpServletResponse res) {
			returns(new ItemForm().getCompanySelectOptions());
		}
	}

	@WebServlet("/spa/validate")
	public static class ValidateServlet extends HttpServlet {
		protected void doPost(HttpServletRequest req, HttpServletResponse res) {
			new ItemForm(req).validate(req);
		}
	}
	
	@WebServlet("/spa/insert")
	public static class InsertServlet extends HttpServlet {
		protected void doPost(HttpServletRequest req, HttpServletResponse res) {
			new ItemForm(req).validate(req).insert();
		}
	}
	
	@WebServlet("/spa/update")
	public static class UpdateServlet extends HttpServlet {
		protected void doPost(HttpServletRequest req, HttpServletResponse res) {
			new ItemForm(req).validate(req).update();
		}
	}
}
