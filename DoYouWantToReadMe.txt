Ставим себе MySQL
Порт: 3306
Логин: root
Пароль: testtest
Создаём там схему: 'your_database' - utf8
Создаем таблицу: 'qr_data'
Создаём поля:
    id - автоинкримент
    phone_number - varchar(20)
    qr_code - TEXT
    shipment_number - varchar(50) UNIQUE