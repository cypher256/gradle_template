CREATE TABLE item (
	id INT AUTO_INCREMENT, 
	name VARCHAR(30), 
	release_date CHAR(10), 
	face_auth BOOLEAN,
	PRIMARY KEY (id)
);
INSERT INTO item (name, release_date, face_auth) VALUES 
	('iPhone 13 Pro Docomo版','2022-09-11',true),
	('iPhone 13 Pro Max Docomo版','2022-12-05',true),
	('Xperia 1 IV 国内版','2022-07-22',false)
;
