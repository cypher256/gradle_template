CREATE TABLE item (
	id INT AUTO_INCREMENT, 
	name VARCHAR(30), 
	release_date CHAR(10), 
	face_auth BOOLEAN,
	PRIMARY KEY (id)
);
INSERT INTO item (name, release_date, face_auth) VALUES 
	('iPhone 14 Pro Docomo版','2023-09-11',true),
	('iPhone 14 Pro Max Docomo版','2023-12-15',true),
	('Xperia 1 V 国内版','2023-07-12',false)
;
