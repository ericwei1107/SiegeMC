package woo.siegePlugin.command;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiegeAdminCommandTest {

    @Test
    void resetscoresRequiresItsDedicatedPermission() {
        SiegeAdminCommand command = command();
        List<String> messages = new ArrayList<>();
        CommandSender sender = sender(true, false, messages);

        List<String> completions = command.tabComplete(sender, new String[]{"admin", "reset"});
        assertEquals(List.of("resetmap"), completions);
        assertFalse(completions.contains("resetscores"));
        assertTrue(command.handle(sender, "siege", new String[]{"admin", "resetscores", "confirm"}));
        assertEquals(List.of("You do not have permission to reset siege scores."), messages);
    }

    @Test
    void resetScoresAppearsForAnAuthorisedAdmin() {
        SiegeAdminCommand command = command();
        CommandSender sender = sender(true, true, new ArrayList<>());

        assertEquals(
                List.of("resetscores", "resetmap"),
                command.tabComplete(sender, new String[]{"admin", "reset"})
        );
    }

    private static SiegeAdminCommand command() {
        return new SiegeAdminCommand(null, null, null, null, null, Logger.getAnonymousLogger());
    }

    private static CommandSender sender(boolean admin, boolean resetScores, List<String> messages) {
        return (CommandSender) Proxy.newProxyInstance(
                CommandSender.class.getClassLoader(),
                new Class<?>[]{CommandSender.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("hasPermission")) {
                        String permission = String.valueOf(arguments[0]);
                        return permission.equals(SiegeAdminCommand.PERMISSION) ? admin
                                : permission.equals(SiegeAdminCommand.RESET_SCORES_PERMISSION) && resetScores;
                    }
                    if (method.getName().equals("sendMessage")) {
                        messages.add(String.valueOf(arguments[0]));
                        return null;
                    }
                    if (method.getName().equals("getName")) {
                        return "test-admin";
                    }
                    if (method.getReturnType() == boolean.class) {
                        return false;
                    }
                    if (method.getReturnType() == int.class) {
                        return 0;
                    }
                    return null;
                }
        );
    }
}
