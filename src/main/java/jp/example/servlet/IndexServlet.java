package jp.example.servlet;

import static jp.example.filter.AutoFlashFilter.*;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * コンテキストルートのトップ画面を表示するインデックス Servlet クラスです。
 * @author New Gradle Project Wizard (c) https://opensource.org/licenses/mit-license.php
 */
@WebServlet("")
public class IndexServlet extends HttpServlet {
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse res) {
		// 必要に応じて、トップ画面に表示するコンテンツなどを取得
		forward("index.jsp");
	}
}
