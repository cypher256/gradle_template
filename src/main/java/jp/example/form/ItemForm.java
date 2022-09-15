package jp.example.form;

import static jp.example.filter.AutoFlashFilter.*;
import static jp.example.filter.AutoTransactionFilter.*;

import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;

import jp.example.entity.Company;
import jp.example.entity.Item;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

/** 
 * アイテムフォームです。
 * @author Pleiades New Gradle Project Wizard
 */
@Data
@NoArgsConstructor
public class ItemForm {
	
	public long id;
	public String name;
	public String releaseDate;
	public boolean faceAuth;
	public long companyId;
	public String companyName;
	
	/**
	 * リクエストパラメーターからフォームを構築します。
	 * @param req コピー元となる HTTP サーブレットリクエスト
	 */
	@SneakyThrows
	public ItemForm(HttpServletRequest req) {
		BeanUtils.populate(this, req.getParameterMap());
	}
	
	/**
	 * エンティティからフォームを構築します。
	 * @param entity コピー元となるアイテムエンティティ
	 */
	@SneakyThrows
	public ItemForm(Item entity) {
		BeanUtils.copyProperties(this, entity);
	}
	
	/**
	 * 入力値を検証します。 <br>
	 * 不正な場合はアプリエラーを表す IllegalStateException をスローします。 
	 * @param req HTTP サーブレットリクエスト (エラー再表示用に、このフォームをリクエスト属性にセット)
	 */
	public ItemForm validate(HttpServletRequest req) {
		req.setAttribute("form", this);
		valid(!name.isBlank(), "製品名は必須です。");
		valid(name.matches("[^<>]+"), "製品名に <> は使用できません。(%d 文字目)", StringUtils.indexOfAny(name, "<>"));
		valid(name.matches(".{10,25}"), "製品名は 10 〜 25 文字で入力してください。(現在 %d 文字)", name.length());
		valid(!(name.matches("(?i).*iphone.*") && !faceAuth), "iPhone は顔認証を有効にしてください。");
		valid(releaseDate.matches("(|.+1.)"), "発売日の日は 10 〜 19 日の範囲で入力してください。");
		return this;
	}
	
	/**
	 * このフォームをエンティティに変換します。
	 * @return エンティティ
	 */
	@SneakyThrows
	public Item toEntity() {
		Item entity = new Item();
		BeanUtils.copyProperties(entity, this);
		return entity;
	}

	/**
	 * 会社 select タグ選択肢を取得します (JSP から呼び出し)。
	 * @return 会社 select タグ選択肢
	 */
	public List<Company> getCompanySelectOptions() {
		return dao().query(Company.class).asc("id").collect();
	}
}
