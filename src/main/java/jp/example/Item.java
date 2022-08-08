package jp.example;

import static jp.example.SingleTierController.*;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.beanutils.BeanUtils;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

/** 
 * item エンティティフォームクラスです。
 * @author Pleiades All in One New Gradle Project Wizard (EPL)
 */
@Data @NoArgsConstructor
public class Item {
	
	public long id;
	public String name;
	public String releaseDate;
	public boolean faceAuth;
	
	@SneakyThrows
	public Item(HttpServletRequest req) {
		BeanUtils.copyProperties(this, req.getParameterMap());
		req.setAttribute("item", this); // エラー時の JSP 再表示用
	}
	
	public Item validate() {
		valid(!name.isBlank(), "製品名は必須です。");
		valid(name.length() <= 30, "製品名は 30 文字以内で入力してください。(%d 文字)", name.length());
		valid(name.matches("[^<>]+"), "製品名に <> は使用できません。");
		valid(!(name.matches("(?i).*iphone.*") && !faceAuth), "iPhone は顔認証を有効にしてください。");
		valid(!releaseDate.endsWith("15"), "発売日は 15 日以外の日付を入力してください。");
		return this;
	}
}
