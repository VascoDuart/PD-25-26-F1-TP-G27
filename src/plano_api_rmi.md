# PLANEAMENTO DE EXPANSÃO (SEÇÃO 5) - INTERAÇÃO COM ESTUDANTES

Este documento define o design da API REST e da Interface RMI para futuras aplicações cliente destinadas aos estudantes, cobrindo os requisitos de autenticação por token.

---

## 1. MECANISMO DE AUTENTICAÇÃO

O sistema utiliza autenticação baseada em token para manter a arquitetura stateless (sem estado) e segura.

### 1.1 Uso do Token
* **API REST**: O token deve ser incluído no cabeçalho **Authorization** com o esquema Bearer para todos os endpoints protegidos.
    * Formato: `Authorization: Bearer <token>`
* **RMI**: O token deve ser passado como o **primeiro parâmetro** em todos os métodos remotos que requerem autenticação.

---

## 2. DEFINIÇÃO DA API REST

A API é baseada em recursos (Substantivos) e utiliza os verbos HTTP para as ações.

### 2.1 Tabela de Síntese de Endpoints REST

| Method | URI | Query Parameters | Description | Request Body | Response Body |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **POST** | `/api/estudantes/login` | None | Autentica o estudante e devolve um token de acesso. | `{"email": "string", "password": "string"}` | `{"token": "string"}` ou `{"status": "ERRO", "mensagem": "..."}` |
| **POST** | `/api/estudantes/logout` | None | Invalida o token atual (sessão) no servidor. | - | Success Message |
| **POST** | `/api/estudantes` | None | Regista uma nova conta de estudante (Público). | Student Registration Data | Created Student Profile |
| **PUT** | `/api/estudantes` | None | Atualiza o perfil/credenciais (Nome, Password) do estudante autenticado. | Updated Profile Data | Updated Profile |
| **GET** | `/api/perguntas/{codigo_acesso}` | None | Obtém detalhes de uma pergunta se estiver **ativa** (disponível para resposta). | - | Question Details |
| **POST** | `/api/perguntas/{codigo_acesso}/respostas` | None | Submete a resposta selecionada do estudante. | `{"selectedOption": "string"}` | Submission Status |
| **GET** | `/api/historico` | `data_inicio` (opcional) | Consulta o histórico de respostas submetidas (perguntas expiradas). | - | List of Answer History |

### 2.2 Detalhe dos Corpos das Mensagens (JSON)

#### 2.2.1 Autenticação (`POST /api/estudantes/login`)
* **Request Body:**
    ```json
    {
      "email": "student@isec.pt",
      "password": "password123"
    }
    ```
* **Response Body (200 OK):**
    ```json
    {
      "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
      "expiresIn": 3600
    }
    ```

#### 2.2.2 Obter pt.isec.pd.tp.estruturas.Pergunta Ativa (`GET /api/perguntas/{codigo_acesso}`)
* **Headers:** `Authorization: Bearer <token>`
* **Response Body (200 OK):**
    ```json
    {
      "accessCode": "A1B2C3",
      "text": "Qual é a capital de Portugal?",
      "options": [
        { "letter": "A", "text": "London" },
        { "letter": "B", "text": "Porto" },
        { "letter": "C", "text": "Lisbon" }
      ]
    }
    ```

#### 2.2.3 Submeter Resposta (`POST /api/perguntas/{codigo_acesso}/respostas`)
* **Headers:** `Authorization: Bearer <token>`
* **Request Body:**
    ```json
    {
      "selectedOption": "C"
    }
    ```
* **Response Body (200 OK):**
    ```json
    {
      "success": true,
      "message": "Resposta submetida com sucesso"
    }
    ```

---

## 3. DEFINIÇÃO DA INTERFACE RMI

A interface RMI espelha as funcionalidades, implementando o token como primeiro parâmetro em chamadas protegidas.

### 3.1 Definição da Interface (StudentService)

```java
package common;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface StudentService extends Remote {

    // --- MÉTODOS PÚBLICOS ---

    /**
     * Autentica o estudante e devolve o token de acesso.
     * @return O token de acesso (String) a ser usado em chamadas subsequentes.
     */
    String login(String email, String password) throws RemoteException;

    /**
     * Regista um novo estudante (Acesso público).
     */
    void register(String studentNumber, String name, String email, String password) throws RemoteException;

    // --- MÉTODOS PROTEGIDOS (REQUEREM TOKEN) ---

    /**
     * Invalida o token atual.
     * @param token O token de autenticação (1º parâmetro obrigatório).
     */
    void logout(String token) throws RemoteException; 

    /**
     * Atualiza o perfil do estudante.
     * @param token O token de autenticação.
     */
    void updateProfile(String token, String name, String password) throws RemoteException;

    /**
     * Obtém uma pergunta ativa.
     * @param token O token de autenticação.
     * @return Objeto com detalhes da pergunta e opções (QuestionDTO).
     */
    Object getQuestion(String token, String accessCode) throws RemoteException;

    /**
     * Submete uma resposta.
     * @param token O token de autenticação.
     */
    void submitAnswer(String token, String accessCode, String selectedOption) throws RemoteException;

    /**
     * Consulta o histórico de respostas do estudante.
     * @param token O token de autenticação.
     * @return Lista de DTOs com detalhes das respostas (AnswerDTO).
     */
    List<Object> getAnswers(String token) throws RemoteException;
}