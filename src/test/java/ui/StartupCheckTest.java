package ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class StartupCheckTest {

    @Test
    void packagedSettingsLoadWithoutAProblem() {
        assertEquals(Optional.empty(), StartupCheck.settingsProblem());
    }
}
