# Интеграция с сервисом, использующем TLS шифрование по ГОСТ-2012

**Цель:** 
Интегрировать Java приложение со сторонним сервисом, использующем TLS шифрование по протоколу ГОСТ-2012.

**Реализация:**
Настройка OpenSSL на поддержку отправки запросов и получения ответа от сервера, использующего TLS шифрование по протоколу ГОСТ-2012. 
Далее отправка запросов и получения ответов при помощи cURL, вызванного из Java кода.

**Реальный пример использования:**
Я реализовал интеграцию при помощи метода выше с сервисом ГИС ЕГРЗ (egrz.ru). Если попытаться зайти через обычный браузер, то получается следующее:

На других ресурсах возможно следующее:



Браузер не поддерживает шифрование по ГОСТ-2012. (Нужно ставить КриптоПРО CSP, установить сертификаты ресурса к которому обращаемся и заходить либо через IE (в случае Windows), [Яндекс Браузер](https://yandex.ru/support/browser-corporate/tls/tls.html) или [Chronium-GOST](https://www.cryptopro.ru/products/chromium-gost)). Таким же образом ГОСТ-2012 не поддерживает ни один Java HTTP клиент.

Первым делом настраиваем сервер.

###### Настройка OpenSSL:

```bash
1) Необходимо подключить GOST-engine (для версий старше 1.1.1). 
В случае младших версий компилить GOST-engine на ветке openssl_1_1_0 или поставить новый OpenSSL, не удаляя старый. (но лучше обновить, дабы не пришлось связывать другой openssl с curl)

# Ubuntu 20.04.2 LTS
# (из коробки) OpenSSL 1.1.1f  31 Mar 2020
# (из коробки) curl 7.68.0

# Компилим GOST-engine

sudo apt install cmake libssl-dev
git clone --branch=openssl_1_1_1 https://github.com/gost-engine/engine.git gost-engine/engine
cd gost-engine/engine
cmake .
make

# Узнаем нужную директорию, копируем туда скомпилированный файл gost.so

openssl version -e
sudo cp bin/gost.so /usr/lib/aarch64-linux-gnu/engines-1.1

# Правим конфиг OpenSSL

sudo cp /etc/ssl/openssl.cnf /etc/ssl/openssl_custom.cnf
sudo vim /etc/ssl/openssl_custom.cnf

# добавляем в начало файла
openssl_conf = openssl_def

# а это в конец
[openssl_def]
engines = engine_section

[engine_section]
gost = gost_section

[gost_section]
engine_id = gost
dynamic_path = /usr/lib/aarch64-linux-gnu/engines-1.1/gost.so
default_algorithms = ALL
CRYPT_PARAMS = id-Gost28147-89-CryptoPro-A-ParamSet

# Обновляем конфиг
export OPENSSL_CONF=/etc/ssl/openssl_custom.cnf

# Проверяем, что engine встал успешно
openssl engine

# должны увидеть в ответе
# (dynamic) Dynamic engine loading support
# (gost) Reference implementation of GOST engine

2) Далее необходимо проимпортировать SSL сертификаты (промежуточные и конечные) сервера, к которому обращаемся. 
Сертификаты предварительно перегнать в формат base64. 
Путь для Ubuntu /etc/ssl/certs/ca-certificates.crt. 
Для других ОС можно посмотреть здесь http://gagravarr.org/writing/openssl-certs/others.shtml.

# Перегоняем серты в base64

openssl x509 -inform der -in certificateDERIN.cer -outform PEM -out certificateBASE64OUT.crt

# Добавляем сертификаты, сохраняем большой файл сертификатов, которым можно доверять

cd /etc/ssl/certs
sudo vim ca-certificates.crt

# Наконец обращаемся к серверу

curl -v https://*адрес*

# В ответе сервера должны увидеть HTTP ответ 200 OK

# После того, как все встало, возможен вызов из Java кода.

# Важно!
# Если на сервере используется TLS v1.0, то на LTS Ubuntu 20.04.02 интеграция невозможна. На старых CentOS все работает нормально.
# Спрашивал на форумах, ответа так и не получил. Мы можем либо разрешить TLS v1.0, либо подключить GOST engine.

# https://stackoverflow.com/questions/67449082/customising-openssl-config-file-to-make-it-work-with-both-custom-engine-and-tls
# https://askubuntu.com/questions/1335768/how-to-configure-curl-openssl-in-order-to-make-it-work-with-custom-engine-and
```

Далее, мы можем делать вызовы из Java кода

Пример использования из Java кода
(простой GET запрос на lk.egrz.ru без заголовков и тела запроса).

```java
String response = CURLRequestProcessor.buildCurlProcessBuilderExecuteAndGetResponse("GET", "https://lk.egrz.ru", null, null, false);
```

Пример отправки с заголовками и JSON телом запроса.

```java
Map<String, String> mapOfHeadersToAdd = new HashMap<String, String>();
mapOfHeadersToAdd.put("Content-Type", "application/json");    
mapOfHeadersToAdd.put("Authorization", "Bearer eyJ2ZXIiOjEsInR5cCI6...z_HkkdA");
List<String> generatedHeaders = CURLRequestProcessor.generateHeadersForCurlRequest(mapOfHeadersToAdd); // генерируем заголовки

String json2Send = "{\"number\":\"Новое заключение экспертизы №123\", \"dateOfIssue\":\"01.01.2001\"}";
List<String> generatedBody = CURLRequestProcessor.generateSimpleBodyForCurlRequest(json2Send); // генерируем JSON тело запроса

String response = CURLRequestProcessor.buildCurlProcessBuilderExecuteAndGetResponse("POST", "https://lk.egrz.ru/api/expertises/", generatedHeaders, generatedBody, false); // отправляем запрос и получаем ответ
```

Пример с заголовками и multipart телом запроса
```java
Map<String, String> mapOfHeadersToAdd = new HashMap<String, String>();
mapOfHeadersToAdd.put("Content-Type", "multipart/form-data");
mapOfHeadersToAdd.put("Authorization", "Bearer eyJ2ZXIiOjEsInR5cCI6...z_HkkdA");
List<String> generatedHeaders = CURLRequestProcessor.generateHeadersForCurlRequest(mapOfHeadersToAdd); // генерируем заголовки

Map<String, String> mapOfMultipartEntities = new HashMap<String, String>();
mapOfMultipartEntities.put("fileMimeType", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"); // MIME тип для .docx очень длинный
mapOfMultipartEntities.put("fileSize", "11950"); // размер файла
mapOfMultipartEntities.put("file", "@/Users/ruslanmussalimov/Desktop/EGRZ_api.docx"); // файл, который нужно отправить "@" означает берем с диска
List<String> generatedMultipartBody = CURLRequestProcessor.generateMultipartBodyForCurlRequest(mapOfMultipartEntities); // генерирует multipart тело

String response = CURLRequestProcessor.buildCurlProcessBuilderExecuteAndGetResponse("POST", "https://lk.egrz.ru/api/file/uploadFile/", generatedHeaders, generatedMultipartBody, false); // отправляем запрос и получаем ответ
```

## Главный вопрос

Почему нельзя использовать КриптоПро JCP? 

На момент интеграции возникла проблема - сервер возвращал не тот сертификат, JCP его не пропускала. Спрашивал на форуме КриптоПро - [Ошибка Caused by: java.security.cert.CertificateException: No name matching found при подключении](https://www.cryptopro.ru/forum2/default.aspx?g=posts&t=17248)

Также пробовали и другие специалисты, результат следующий:

>Ранее было опробовано решение с JCP от Крипто-про. Однако в этом случае при отправке запроса на открытие защищенного соединения TLS, сервер отвечает не тем сертификатом, который мы ожидаем получить для проверки(lk.egrz.ru), в итоге handshake не происходит, а происходит обрыв соединения. Возможно, нам отвечает сервер, обрабатывающий начальный запрос и распределяющий запросы до конечных точек и высылает свой собственный сертификат.

На форуме ответили, что дело в [SNI](https://ru.wikipedia.org/wiki/Server_Name_Indication), КриптоПро его не поддерживает. Эту теорию также подтвердили форумчане с security.stackexchange по следующей ссылке:
[SSL server sends wrong certificate when accessed via Java](https://security.stackexchange.com/questions/222575/ssl-server-sends-wrong-certificate-when-accessed-via-java)

В данный момент, JCP, возможно, поддерживает SNI, но это проприетарное ПО, а данное решение - бесплатное.

## Использование в проекте

На мой взгляд, компилить jar-файл из одного класса - перебор. Просто добавьте класс в проект.

## Контрибутинг

Класс открыт для внесения правок и улучшений.


## Связь со мной

Если у вас что-то не получается или у вас просто есть вопрос по процессу - напишите, пожалуйста, в Telegram [@rus9mus9](https://t.me/rus9mus9), обязательно отвечу! 