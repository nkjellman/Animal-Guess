//This animal-guessing program illustrates the use of the binary tree node class.
//Created by Michael Main, Jul 22, 2005
//Modified by James Vanderhyde, 2 December 2009
// Various fixes and updates
//Modified by James Vanderhyde, 7 December 2010
// Various fixes and updates
//Modified by James Vanderhyde, 25 October 2011
// 1. Changed to use a database instead of a binary tree
// 2. Changed name of query method to userYesOrNo
//    to avoid confusion with database queries
//Modified by James Vanderhyde, 12 November 2015
// Fixed up some code and prepared for CMPSC 321 at SXU

package animalguess;

import java.sql.*;
import java.util.Scanner;

/******************************************************************************
 * The <CODE>AnimalGuess</CODE> Java application illustrates the use of
 * the binary tree node class is a small animal-guessing game.
 *
 * @author Michael Main
 *   <A HREF="mailto:main@colorado.edu"> (main@colorado.edu) </A>
 *
 * @version
 *   Jul 22, 2005
 ******************************************************************************/
public class AnimalGuess
{

    private static final Scanner cin = new Scanner(System.in);

    private static String password()
    {
        return "778570";
    }

    /**
     * The main method prints instructions and repeatedly plays the
     * animal-guessing game. As the game is played, the taxonomy tree
     * grows by learning new animals. The <CODE>String</CODE> argument
     * (<CODE>args</CODE>) is not used in this implementation.
     * @param args Not used
     **/
    public static void main(String[] args)
    {
        //Load database driver
        try
        {
            Class.forName("com.mysql.jdbc.Driver").newInstance();
        }
        catch (Exception e)
        {
            System.err.println("Error when loading DB driver:");
            System.err.println(e);
        }
        
        //Establish database connection
        Connection conn = null;
        try
        {
            final String username = "nk57001";
            final String url = "jdbc:mysql://csmaster.sxu.edu/"+username;
            conn = DriverManager.getConnection(url,username,password());
            //System.out.println("Connection to database established.");
        }
        catch (SQLException e)
        {
            System.err.println("SQL exception: " + e.getMessage());
            System.err.println("SQL state: " + e.getSQLState());
            System.err.println("Error code: " + e.getErrorCode());
        }

        if (conn != null)
        {
            //Print instructions
            instruct();

            //Play the game, until the user wants to quit
            do
            {
                try
                {
                    play(conn);
                }
                catch (SQLException e)
                {
                    System.out.println("Game interrupted due to database error.");
                    System.err.println("SQL exception: " + e.getMessage());
                    System.err.println("SQL state: " + e.getSQLState());
                    System.err.println("Error code: " + e.getErrorCode());
                    e.printStackTrace(System.err);
                }
            }
            while (userYesOrNo("Shall we play again?"));

            //Close database connection
            try
            {
                conn.close();
                //System.out.println("Database connection closed.");
            }
            catch (SQLException e)
            {
            }

            //Take leave of the player
            System.out.println("Thanks for teaching me a thing or two.");
        }
    }

    /**
     * Print instructions for the animal-guessing game.
     **/
    public static void instruct()
    {
        System.out.println("Please think of an animal.");
        System.out.println("I will ask some yes/no questions to try to figure");
        System.out.println("out what you are.");
    }

    /**
     * Play one round of the animal guessing game.
     * @param db
     *   the database connection used to access the taxonomy tree data.
     * <dt><b>Precondition:</b><dd>
     *   <CODE>db</CODE> is not null and is a valid database connection
     * <dt><b>Postcondition:</b><dd>
     *   The method has played one round of the game, and possibly
     *   added new information about a new animal to the database.
     * @throws SQLException if any database errors occur
     **/
    public static void play(Connection db) throws SQLException
    {
        PreparedStatement p = db.prepareStatement(
                  "SELECT text, yes, no "
                + "FROM taxonomy "
                + "WHERE id = ?");
        
        ResultSet r;

        //Data from the current taxonomy record
        int current = 1; //root of taxonomy tree
        int yes, no;
        String text;
        boolean isAnimal;

        //Read root record from database
        p.setInt(1, current);
        r = p.executeQuery();
        if (r.next())
        {
            text = r.getString("text");
            yes = r.getInt("yes");
            no = r.getInt("no");
            isAnimal = r.wasNull();
        }
        else
        {
            //The root was not found in the db; can't play the game
            yes=no=0;
            text=null;
            isAnimal=true; //to skip the loop
        }
        
        //Descend in the tree by asking questions.
        while (!isAnimal)
        {
            //Ask the user a question
            if (userYesOrNo(text))
                current = yes;
            else
                current = no;
            
            //Read next record from database
            p.setInt(1, current);
            r = p.executeQuery();
            if (r.next())
            {
                text = r.getString("text");
                yes = r.getInt("yes");
                no = r.getInt("no");
                isAnimal = r.wasNull();
            }
            else
            {
                //The specified ID number was not found in the db
                yes=no=0;
                text=null;
                isAnimal=true; //to break out of the loop
            }
        }
        
        if (text != null)
        {
            //When we reach an animal, make a guess.
            System.out.print("My guess is " + text + ". ");

            //Learn about a new animal or brag.
            if (!userYesOrNo("Am I right?"))
                learn(db, current);
            else
                System.out.println("I knew it all along!");
        }
        else
        {
            System.out.println("Error in database: Can't find ID "+current);
        }
        
        //Release JDBC resources used by the statement        
        try
        {
            p.close();
        }
        catch (SQLException e)
        {
        }
    }

    /**
     * Elicits information from the user to improve the taxonomy tree.
     * @param db
     *   the database connection used to access the taxonomy tree data.
     * @param guessID
     *   a reference to an animal record of the taxonomy tree
     * <dt><b>Precondition:</b><dd>
     *   <CODE>guessID</CODE> is an ID of an animal in a taxonomy tree. This
     *   record contains a wrong guess that was just made.
     * <dt><b>Postcondition:</b><dd>
     *   Information has been elicited from the user, and the tree has
     *   been improved in the database.
     * @throws SQLException if any database errors occur
     **/
    public static void learn(Connection db, int guessID) throws SQLException
    {
        String correctAnimal; // The animal that the user was thinking of
        String newQuestion;   // A question to distinguish the two animals
        boolean answerIsYes;

        // Get Strings for the correct animal and a new question.
        System.out.println("I give up. What are you?");
        correctAnimal = cin.nextLine();
        System.out.println("Help me learn by entering a question that can "
                + "tell the difference between my guess and "+correctAnimal+".");
        newQuestion = cin.nextLine();
        System.out.println("As a "+correctAnimal+",");
        answerIsYes = userYesOrNo(newQuestion);

        // Put one new question and one new animal into the database.
        PreparedStatement insertStmt = db.prepareStatement("INSERT INTO nk57001.TAXONOMY (text)" 
                + "VALUES (?)",  Statement.RETURN_GENERATED_KEYS);
                
        PreparedStatement updateStmt = db.prepareStatement(
                  "UPDATE nk57001.TAXONOMY SET text=?, yes=?, no=? WHERE id = ?");
        /*PreparedStatement maxIdStmt = db.prepareStatement(
                  "SELECT MAX(id) AS MaxID "
                + "FROM taxonomy ");*/
        PreparedStatement selectStmt = db.prepareStatement(
                  "SELECT text, yes, no FROM nk57001.TAXONOMY WHERE id = ?");
        ResultSet rs;
        //String command;
        //Get max id from database
        //rs = maxIdStmt.executeQuery();
        int guessAnimalID = 0;
        int correctAnimalID = 0;
        String oldAnimal = "";
        //Get name of the last guessed animal
        selectStmt.setInt(1, guessID);
        rs = selectStmt.executeQuery();
        if (rs.next())
            oldAnimal = rs.getString("text");
        
        //Insert new animal into database
        insertStmt.setString(1, correctAnimal);
        insertStmt.executeUpdate();
        rs = insertStmt.getGeneratedKeys();
        if (rs.next())
            correctAnimalID = rs.getInt(1);
        
        //Insert old animal into database
        insertStmt.setString(1, oldAnimal);
        insertStmt.executeUpdate();
        rs = insertStmt.getGeneratedKeys();
        if (rs.next())
            guessAnimalID = rs.getInt(1);
        
        //Insert new question into database
        //Update the last question to reference the new question
        updateStmt.setString(1, newQuestion);
        if (answerIsYes)
        {
            updateStmt.setInt(2, correctAnimalID);
            updateStmt.setInt(3, guessAnimalID);
        }
        else
        {
            updateStmt.setInt(2, guessAnimalID);
            updateStmt.setInt(3, correctAnimalID);
        }
        updateStmt.setInt(4, guessID);
        updateStmt.executeUpdate();
    }

    /**
     * Asks a question of the user and waits for Y/N on standard input.
     * @param prompt The question that is displayed to standard output
     * @return true if the user entered Y, false if the user entered N.
     */
    public static boolean userYesOrNo(String prompt)
    {
        String answer;

        System.out.print(prompt + " [Y or N]: ");
        answer = cin.nextLine().toUpperCase();
        while (!answer.startsWith("Y") && !answer.startsWith("N"))
        {
            System.out.print("Invalid response. Please type Y or N: ");
            answer = cin.nextLine().toUpperCase();
        }

        return answer.startsWith("Y");
    }

}
