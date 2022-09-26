package jp.example.servlet;

import static jp.example.filter.AutoFlashFilter.*;
import static jp.example.filter.AutoTransactionFilter.*;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import jp.example.entity.Item;
import jp.example.form.ItemForm;

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
			dao().delete(new ItemForm(req).toEntity(new Item())); // レスポンス - 正常時:なし、異常時:エラーメッセージ
		}
	}
	
	@WebServlet("/spa/detail")
	public static class DetailServlet extends HttpServlet {
		protected void doGet(HttpServletRequest req, HttpServletResponse res) {
			returns(new ItemForm(req).findFormById());
		}
	}
	
	@WebServlet("/spa/companySelect")
	public static class CompanySelectServlet extends HttpServlet {
		protected void doGet(HttpServletRequest req, HttpServletResponse res) {
			returns(new ItemForm().getCompanySelectOptions());
		}
	}

	@WebServlet("/spa/validate")
	public static class ValidateServlet extends HttpServlet {
		protected void doPost(HttpServletRequest req, HttpServletResponse res) {
			new ItemForm(req).validate(req); // レスポンス - 正常時:なし、異常時:エラーメッセージ
		}
	}
	
	@WebServlet("/spa/insert")
	public static class InsertServlet extends HttpServlet {
		protected void doPost(HttpServletRequest req, HttpServletResponse res) {
			ItemForm form = new ItemForm(req).validate(req);
			dao().insert(form.toEntity(new Item())); // レスポンス - 正常時:なし、異常時:エラーメッセージ
		}
	}
	
	@WebServlet("/spa/update")
	public static class UpdateServlet extends HttpServlet {
		protected void doPost(HttpServletRequest req, HttpServletResponse res) {
			ItemForm form = new ItemForm(req).validate(req);
			dao().update(form.toEntity(form.findEntityById())); // レスポンス - 正常時:なし、異常時:エラーメッセージ
		}
	}
}
