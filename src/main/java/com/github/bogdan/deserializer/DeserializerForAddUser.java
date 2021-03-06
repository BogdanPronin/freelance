package com.github.bogdan.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.github.bogdan.model.Role;
import com.github.bogdan.model.User;
import com.google.i18n.phonenumbers.NumberParseException;
import org.mindrot.jbcrypt.BCrypt;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;

import static com.github.bogdan.service.ContactService.*;
import static com.github.bogdan.service.DeserializerService.*;
import static com.github.bogdan.service.LocalDateService.checkAge;
import static com.github.bogdan.service.LocalDateService.checkLocalDateFormat;
import static com.github.bogdan.service.UserService.*;

public class DeserializerForAddUser extends StdDeserializer<User> {

    public DeserializerForAddUser() {
        super(User.class);
    }

    @Override
    public User deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
        try {
            JsonNode node = jsonParser.getCodec().readTree(jsonParser);
            User u = new User();

            String fname = getStringFieldValue(node,"fname");
            u.setFname(fname);

            String lname = getStringFieldValue(node,"lname");
            u.setLname(lname);

            String login = getStringFieldValue(node,"login");
            u.setLogin(login);
            checkIsLoginInUse(login);


            String email = getStringFieldValue(node,"email");
            u.setEmail(email);
            checkValidateEmail(email);
            checkIsEmailAlreadyInUse(email);

            String phone = getStringFieldValue(node,"phone");
            u.setPhone(phone);
            checkValidatePhone(phone);
            checkIsPhoneAlreadyInUse(phone);

            LocalDate localDate = LocalDate.now();
            u.setDateOfRegister(localDate.toString());

            String dateOfBirthday = getStringFieldValue(node,"dateOfBirthday");
            checkLocalDateFormat(dateOfBirthday);
            checkAge(dateOfBirthday);
            u.setDateOfBirthday(dateOfBirthday);

            u.setRole(Role.USER);

            String password = getStringFieldValue(node,"password");
            String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt(12));
            u.setPassword(hashedPassword);


            return u;

        } catch (SQLException | NumberParseException e) {
            e.printStackTrace();
        }
        return null;
    }
}
