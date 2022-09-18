package jp.example.entity;

import lombok.Data;

/** 
 * アイテムエンティティです。
 * @author Pleiades New Gradle Project Wizard (c) MPL
 */
@Data
public class Item {
	
	public long id;
	public String name;
	public String releaseDate;
	public boolean faceAuth;
	public long companyId;
}
