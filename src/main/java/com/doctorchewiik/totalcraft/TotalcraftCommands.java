package com.doctorchewiik.totalcraft;

import com.doctorchewiik.totalcraft.army.ArmyData;
import com.doctorchewiik.totalcraft.army.ArmyProfile;
import com.doctorchewiik.totalcraft.army.ArmyService;
import com.doctorchewiik.totalcraft.battle.Battle;
import com.doctorchewiik.totalcraft.battle.BattleManager;
import com.doctorchewiik.totalcraft.data.GameData;
import com.doctorchewiik.totalcraft.network.BattleActionPayload;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.stream.Stream;

final class TotalcraftCommands {
    private TotalcraftCommands() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("totalcraft")
                .then(Commands.literal("armee").executes(ctx -> {
                    ArmyService.sendProfile(ctx.getSource().getPlayerOrException(), true);
                    return 1;
                }))
                .then(Commands.literal("bataille")
                        .executes(ctx -> {
                            BattleManager.start(ctx.getSource().getPlayerOrException(), "");
                            return 1;
                        })
                        .then(Commands.argument("carte", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        Stream.concat(Stream.of("aleatoire"), GameData.MAPS.keySet().stream()), builder))
                                .executes(ctx -> {
                                    BattleManager.start(ctx.getSource().getPlayerOrException(),
                                            StringArgumentType.getString(ctx, "carte"));
                                    return 1;
                                })))
                .then(Commands.literal("rejoindre")
                        .then(Commands.argument("joueur", EntityArgument.player()).executes(ctx -> {
                            ServerPlayer self = ctx.getSource().getPlayerOrException();
                            ServerPlayer host = EntityArgument.getPlayer(ctx, "joueur");
                            Battle battle = BattleManager.battleOf(host);
                            if (battle == null) {
                                ctx.getSource().sendFailure(Component.literal(
                                        host.getGameProfile().getName() + " n'est pas en bataille."));
                                return 0;
                            }
                            BattleManager.join(self, battle);
                            return 1;
                        })))
                .then(Commands.literal("abandonner").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    BattleManager.handleAction(player, new BattleActionPayload(BattleActionPayload.SURRENDER, 0, 0));
                    return 1;
                }))
                .then(Commands.literal("quitter").executes(ctx -> {
                    BattleManager.leave(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("or")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("montant", IntegerArgumentType.integer())
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    giveGold(player, IntegerArgumentType.getInteger(ctx, "montant"));
                                    return 1;
                                })
                                .then(Commands.argument("joueur", EntityArgument.player()).executes(ctx -> {
                                    giveGold(EntityArgument.getPlayer(ctx, "joueur"),
                                            IntegerArgumentType.getInteger(ctx, "montant"));
                                    return 1;
                                })))));
    }

    private static void giveGold(ServerPlayer player, int amount) {
        ArmyData data = ArmyData.get(player.server);
        ArmyProfile profile = data.profile(player.getUUID());
        profile.money = Math.max(0, profile.money + amount);
        data.setDirty();
        player.sendSystemMessage(Component.literal("Trésor : " + profile.money + " pièces d'or")
                .withStyle(ChatFormatting.GOLD));
        ArmyService.sendProfile(player, false);
    }
}
