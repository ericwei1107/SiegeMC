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
    void retiredResetCommandsAreNotSuggested() {
        SiegeAdminCommand command = command();
        List<String> messages = new ArrayList<>();
        CommandSender sender = sender(true, false, messages);

        List<String> completions = command.tabComplete(sender, new String[]{"admin", "reset"});
        assertEquals(List.of(), completions);
        assertTrue(command.handle(sender, "siege", new String[]{"admin", "resetscores", "confirm"}));
        assertEquals(List.of("Usage: /siege admin <setbanner|savekit|supply|rotation|map>"), messages);
    }

    @Test
    void rotationCommandsReplaceManualResetWorkflows() {
        SiegeAdminCommand command = command();
        CommandSender sender = sender(true, true, new ArrayList<>());

        assertEquals(
                List.of("rotation"),
                command.tabComplete(sender, new String[]{"admin", "rot"})
        );
    }

    @Test
    void saveKitOffersConfirmationCompletion() {
        SiegeAdminCommand command = command();
        CommandSender sender = sender(true, true, new ArrayList<>());

        assertEquals(
                List.of("confirm"),
                command.tabComplete(sender, new String[]{"admin", "savekit", "c"})
        );
    }

    @Test
    void supplyCommandsOfferClaimAndTeamCompletions() {
        SiegeAdminCommand command = command();
        CommandSender sender = sender(true, true, new ArrayList<>());

        assertEquals(
                List.of("claim"),
                command.tabComplete(sender, new String[]{"admin", "supply", "cl"})
        );
        assertEquals(
                List.of("blue"),
                command.tabComplete(sender, new String[]{"admin", "supply", "claim", "b"})
        );
    }

    private static SiegeAdminCommand command() {
        return new SiegeAdminCommand(null, null, null, null, Logger.getAnonymousLogger(), null, null, null, null);
    }

    private static CommandSender sender(boolean admin, boolean resetScores, List<String> messages) {
        return (CommandSender) Proxy.newProxyInstance(
                CommandSender.class.getClassLoader(),
                new Class<?>[]{CommandSender.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("hasPermission")) {
                        String permission = String.valueOf(arguments[0]);
                        return permission.equals(SiegeAdminCommand.PERMISSION) && admin;
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
