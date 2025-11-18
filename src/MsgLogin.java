public class MsgLogin extends Mensagem {
    private static final long serialVersionUID = 1L;

    private final String email;
    private final String password;

    public MsgLogin(String email, String password) {
        this.email = email;
        this.password = password;
    }

    public String getEmail() { return email; }
    public String getPassword() { return password; }
}