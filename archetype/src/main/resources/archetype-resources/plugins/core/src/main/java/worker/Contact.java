#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package}.worker;

import com.opencsv.CSVReader;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.stream.Stream;

public class Contact {
    public final String name;
    public final String email;
    public final String phone;

    public Contact(String name, String email, String phone) {
        this.name = name;
        this.email = email;
        this.phone = phone;
    }

    public static Stream<Contact> readContacts(File file) throws IOException {
        try (CSVReader reader = new CSVReader(new FileReader(file))) {
            return reader.readAll().stream()
                         .map(line -> new Contact(line[0], line[1], line[2]));
        }
    }

    public static boolean containsContacts(File file) throws IOException {
        try (CSVReader reader = new CSVReader(new FileReader(file))) {
            return reader.readNext().length == 3;
        }
    }
}
