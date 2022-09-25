package jp.example.servlet;

import static jp.example.filter.AutoFlashFilter.*;
import static jp.example.filter.AutoTransactionFilter.*;

import java.util.List;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import jp.example.entity.Company;
import jp.example.entity.Item;
import jp.example.form.ItemForm;
import lombok.Data;

public class SpaCrudServlet {

	@WebServlet("/react/search")
	public static class SearchServlet extends HttpServlet {
		protected void doGet(HttpServletRequest req, HttpServletResponse res) {
			returns(new ItemForm(req).findFormList());
		}
	}

	@WebServlet("/react/count")
	public static class CountServlet extends HttpServlet {
		protected void doGet(HttpServletRequest req, HttpServletResponse res) {
			returns("結果予想件数: " + new ItemForm(req).count() + " 件");
		}
	}
	
	@WebServlet("/react/delete")
	public static class DeleteServlet extends HttpServlet {
		protected void doPost(HttpServletRequest req, HttpServletResponse res) {
			dao().delete(new ItemForm(req).toEntity(new Item()));
			returns("️ℹ️ 削除しました。");
		}
	}
	
	@WebServlet("/react/detail")
	public static class DetailServlet extends HttpServlet {
		protected void doGet(HttpServletRequest req, HttpServletResponse res) {
			@Data class DetailhResult {
				ItemForm form = new ItemForm(req).findFormById();
				List<Company> companySelect = new ItemForm().getCompanySelectOptions();
			}
			returns(new DetailhResult());
		}
	}

	@WebServlet("/react/validate")
	public static class RestServlet extends HttpServlet {
		protected void doPost(HttpServletRequest req, HttpServletResponse res) {
			new ItemForm(req).validate(req);
		}
	}
}
