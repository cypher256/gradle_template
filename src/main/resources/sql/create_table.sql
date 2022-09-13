-- ============================================================================
-- データベース初期データ (起動時に AutoTransactionFilter で読み込み)
-- ============================================================================

-- ----------------------------------------------------------------------------
-- ユーザー (ログイン認証: shiro.ini 参照)
-- ----------------------------------------------------------------------------
CREATE TABLE users (
	id BIGINT AUTO_INCREMENT,
	username VARCHAR(128),
	password VARCHAR(128),
	PRIMARY KEY (id)
);
INSERT INTO users (username, password) VALUES 
	('demouser','demo1234@')
;

-- ----------------------------------------------------------------------------
-- アイテム (CRUD サンプル)
-- ----------------------------------------------------------------------------
CREATE TABLE item (
	id BIGINT AUTO_INCREMENT, 
	name VARCHAR(30), 
	release_date CHAR(10), 
	face_auth BOOLEAN,
	company_id BIGINT,
	PRIMARY KEY (id)
);
INSERT INTO item (name, release_date, face_auth, company_id) VALUES 
	('iPhone 14 Pro Docomo版','2022-09-11',true,1),
	('iPhone 14 Pro Max Docomo版','2022-12-15',true,1),
	('Xperia 1 V 国内版','2022-07-12',false,2)
;

-- ----------------------------------------------------------------------------
-- 会社 (CRUD サンプル)
-- ----------------------------------------------------------------------------
CREATE TABLE company (
	id BIGINT AUTO_INCREMENT, 
	company_name VARCHAR(30), 
	PRIMARY KEY (id)
);
INSERT INTO company (id, company_name) VALUES 
	(1,'Apple'),
	(2,'Sony')
;
