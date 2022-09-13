package jp.example.entity;

import lombok.Data;

/** 
 * アイテムエンティティです。
 * @author New Gradle Project Wizard (c) https://opensource.org/licenses/mit-license.php
 */
@Data
public class Item {
	
	public long id;
	public String name;
	public String releaseDate;
	public boolean faceAuth;
	public long companyId;
}
