package jp.example.dto;

import static jp.example.filter.AutoControlFilter.*;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

/** 
 * item DTO クラスです。
 * @author Pleiades All in One New Gradle Project Wizard (EPL)
 */
@Data
@NoArgsConstructor
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
		valid(name.matches("[^<>]+"), "製品名に <> は使用できません。(%d 文字目)", StringUtils.indexOfAny(name, "<>"));
		valid(name.matches(".{10,25}"), "製品名は 10 〜 25 文字で入力してください。(現在 %d 文字)", name.length());
		valid(!(name.matches("(?i).*iphone.*") && !faceAuth), "iPhone は顔認証を有効にしてください。");
		valid(releaseDate.matches("(|.+1.)"), "発売日の日は 10 〜 19 日の範囲で入力してください。");
		return this;
	}
}
