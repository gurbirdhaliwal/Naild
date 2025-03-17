import java.sql.*;

public class MySQLConnection {
    public static void main(String[] args) {
        String url = "jdbc:mysql://127.0.0.1:3306/User";
        String user = "root";
        String password = "REMOVED-ROTATED-DB-PASSWORD";
        try (Connection connection = DriverManager.getConnection(url, user, password)){
            if (connection != null) {
                System.out.println("Connection to Database was succesful!");
            }
        }catch (SQLException e) {
            System.out.println("Connection Failed!");
            e.printStackTrace();
        }
    }
}
