package net.velevators.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public final class VElevatorsBasicCommand implements BasicCommand {
    private final ElevatorCommand delegate;

    public VElevatorsBasicCommand(ElevatorCommand delegate) {
        this.delegate = delegate;
    }

    @Override
    public void execute(@NotNull CommandSourceStack source, @NotNull String[] args) {
        delegate.execute(source.getSender(), args);
    }

    @Override
    public @NotNull Collection<String> suggest(@NotNull CommandSourceStack source, @NotNull String[] args) {
        return delegate.complete(source.getSender(), args);
    }

    @Override
    public boolean canUse(@NotNull CommandSender sender) {
        return true;
    }
}
