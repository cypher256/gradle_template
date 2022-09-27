package jp.example.form;

import static jp.example.filter.AutoFlashFilter.*;
import static jp.example.filter.AutoTransactionFilter.*;
import static org.apache.commons.lang3.StringUtils.*;

import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;

import jp.example.entity.Company;
import jp.example.entity.Item;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/** 
 * アイテムフォームモデルです。
 * <pre>
 * アプリでスローした例外は AutoFlashFilter で下記の処理が行われ、JSP では ${MESSAGE} で例外メッセージを取得できます。
 * 
 *   IllegalStateException の場合: 入力画面にフォワード
 *   java.lang.Error の場合: 直近のリダイレクト先またはトップへリダイレクト
 *   
 * </pre>
 * @author New Gradle Project Wizard (c) Pleiades MIT
 */
@Data
@NoArgsConstructor
@Slf4j
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
		log.debug("リクエスト {}", this);
		// hidden の id をセキュアにする場合はセッション保持や暗号化が必要
	}
	
	/**
	 * エンティティからフォームを構築します。
	 * @param sourceEntity コピー元となるエンティティ
	 */
	@SneakyThrows
	public ItemForm(Item sourceEntity) {
		BeanUtils.copyProperties(this, sourceEntity);
	}
	
	/**
	 * このフォームの値を指定したエンティティに上書きコピーします。
	 * @param targetEntity コピー先となるエンティティ
	 * @return 引数のエンティティ
	 */
	@SneakyThrows
	public Item copyTo(Item targetEntity) {
		BeanUtils.copyProperties(targetEntity, this);
		return targetEntity;
	}
	
	/**
	 * 入力値を検証します。 <br>
	 * 登録、変更画面共通の入力チェックを行い、不正な場合はアプリエラーを表す IllegalStateException をスローします。 
	 * @param req HTTP サーブレットリクエスト
	 * @return このインスタンス
	 */
	public ItemForm validate(HttpServletRequest req) {
		
		// エラーの場合は例外がスローされるため、画面に再表示する入力値を先にセット
		req.setAttribute("form", this);
		
		// 形式チェック
		valid(!name.isBlank(), "製品名は必須です。");
		valid(name.matches("[^<>]+"), "製品名に <> は使用できません。(%d 文字目)", StringUtils.indexOfAny(name, "<>"));
		valid(name.matches(".{10,25}"), "製品名は 10 〜 25 文字で入力してください。(現在 %d 文字)", name.length());
		valid(!(name.matches("(?i).*iphone.*") && !faceAuth), "iPhone は顔認証を有効にしてください。");
		valid(releaseDate.matches(".+1."), "発売日の日は 10 〜 19 日の範囲で入力してください。");
		
		// DB 相関チェック (id: 変更時は自身を除外、登録時は 0 で DB に存在しないため実質 name 条件のみ)
		dao().query(Item.class).notEqual("id", id).equal("name", name).exists(() -> {
			throw new IllegalStateException("指定された製品名は、別の製品で使用されています。");
		});
		return this;
	}
	
	/**
	 * このフォームの id を条件にエンティティを取得します。
	 * @return アイテムエンティティ
	 */
	public Item findEntityById() {
		return dao().find(Item.class, id).orElseThrow(() -> new Error("指定された製品は、すでに削除されています。"));
	}
	
	/**
	 * このフォームの id を条件にエンティティを取得し、フォームに変換して返します。
	 * @return アイテムフォーム
	 */
	public ItemForm findFormById() {
		return new ItemForm(findEntityById());
	}
	
	/**
	 * このフォームを条件としてアイテムフォームリストを取得します。
	 * @return アイテムフォームリスト
	 */
	public List<ItemForm> findFormList() {
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
				LIMIT 100
			""";
		List<ItemForm> formList = dao().queryWith(sql).paramBean(this).collect(ItemForm.class);
		log.debug("WHERE name={} releaseDate={} \n{}", name, releaseDate, joinWith("\n", formList.toArray()));
		return formList;
	}
	
	/**
	 * このフォームを条件としてアイテム件数を取得します。
	 * @return 件数
	 */
	public long count() {
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
		return dao().queryWith(sql).paramBean(this).one(long.class);
	}
	
	/**
	 * 会社 select タグ選択肢を取得します (JSP から呼び出し)。
	 * @return 会社 select タグ選択肢
	 */
	public List<Company> getCompanySelectOptions() {
		return dao().query(Company.class).asc("id").collect();
	}
}
