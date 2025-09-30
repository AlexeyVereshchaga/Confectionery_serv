# Аутентификация (Auth API)

Все эндпоинты в этом разделе работают с JSON.
Аутентификация по access-токену осуществляется через HTTP-заголовок:

```
Authorization: Bearer <access_token>
```

---

## Регистрация пользователя

**POST** `/register`

Создаёт нового пользователя (админа или обычного).

### Тело запроса

```json
{
  "email": "user@example.com",
  "password": "secret123",
  "isAdmin": false
}
```

### Ответ

```json
{
  "accessToken": "<jwt>",
  "refreshToken": "<refresh>"
}
```

### Ошибки

* `409 Conflict` — пользователь уже существует.

---

## Получение токенов (логин)

**POST** `/token`

Авторизация по email + пароль. Возвращает пару токенов.

### Тело запроса

```json
{
  "email": "user@example.com",
  "password": "secret123"
}
```

### Ответ

```json
{
  "accessToken": "<jwt>",
  "refreshToken": "<refresh>"
}
```

### Ошибки

* `401 Unauthorized` — неверные данные.

---

## Обновление токена (вариант 1)

**POST** `/refresh`

Обновление access/refresh токенов по refresh-токену.

### Тело запроса

```json
{
  "refreshToken": "<refresh>"
}
```

### Ответ

```json
{
  "accessToken": "<jwt>",
  "refreshToken": "<refresh>"
}
```

### Ошибки

* `401 Unauthorized` — неверный refresh токен.

---

## Выход из системы

**POST** `/logout`

Завершает сессию (удаляет access-токен из базы).

### Заголовки

```
Authorization: Bearer <access_token>
```

### Ответы

* `200 OK` — успешный выход.
* `401 Unauthorized` — недействительный или отсутствующий токен.

---

## Обновление токена (вариант 2, с проверкой срока)

**POST** `/refresh` (альтернативный обработчик)

### Тело запроса

```json
{
  "refreshToken": "<refresh>"
}
```

### Ответ

```json
{
  "accessToken": "<new_access>",
  "refreshToken": "<new_refresh>"
}
```

### Ошибки

* `401 Unauthorized` — неверный или истёкший refresh токен.
