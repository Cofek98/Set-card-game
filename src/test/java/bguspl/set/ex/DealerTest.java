package bguspl.set.ex;

import bguspl.set.Config;
import bguspl.set.Env;
import bguspl.set.UserInterface;
import bguspl.set.Util;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)

public class DealerTest {

    Dealer dealer;

    @Mock
    Util util;
    @Mock
    private UserInterface ui;
    @Mock
    private Table table;
    @Mock
    private Logger logger;



    @BeforeEach
    void setUp() {
        // purposely do not find the configuration files (use defaults here).
        Env env = new Env(logger, new Config(logger, (String) null), ui, util);
        Player[] players = new Player[env.config.players];
        dealer = new Dealer(env, table, players);
    }

    @Test
    void addSetCheck_size() {
        int expectedSize = dealer.setCheck.size();

        if (!dealer.setCheck.contains(0))
            expectedSize = dealer.setCheck.size() + 1;

        dealer.addSetCheck(0);

        assertEquals(expectedSize, dealer.setCheck.size());
    }

    @Test
    void addSetCheck_player() {

        boolean contains = dealer.setCheck.contains(0);

        if (!contains) {
            contains = true;
            dealer.addSetCheck(0);
        }

        assertEquals(contains, dealer.setCheck.contains(0));
    }

}
