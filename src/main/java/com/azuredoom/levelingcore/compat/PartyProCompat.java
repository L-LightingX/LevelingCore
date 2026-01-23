package com.azuredoom.levelingcore.compat;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.util.Config;
import me.tsumori.partypro.party.PartyInfo;
import me.tsumori.partypro.party.PartyManager;

import java.util.Arrays;
import java.util.UUID;

import com.azuredoom.levelingcore.config.GUIConfig;
import com.azuredoom.levelingcore.level.LevelServiceImpl;
import com.azuredoom.levelingcore.ui.hud.XPBarHud;
import com.azuredoom.levelingcore.utils.NotificationsUtil;

public class PartyProCompat {

    private PartyProCompat() {}

    public static void onXPGain(
        long xp,
        UUID playerUuid,
        LevelServiceImpl levelService,
        Config<GUIConfig> config,
        PlayerRef playerRef
    ) {
        var party = getPartyFromPlayer(playerUuid);
        if (party != null && config.get().isEnablePartyProXPShareCompat()) {
            Arrays.stream(party.getAllPartyMembers())
                .distinct()
                .forEach(uuid -> {
                    if (!config.get().isDisableXPGainNotification())
                        NotificationsUtil.sendNotification(Universe.get().getPlayer(uuid), "Gained " + xp + " XP");
                    levelService.addXp(uuid, xp);
                    XPBarHud.updateHud(playerRef);
                });
        } else {
            if (!config.get().isDisableXPGainNotification())
                NotificationsUtil.sendNotification(playerRef, "Gained " + xp + " XP");
            levelService.addXp(playerUuid, xp);
            XPBarHud.updateHud(playerRef);
        }
    }

    private static PartyInfo getPartyFromPlayer(UUID playerUuid) {
        return PartyManager.getInstance()
            .getParties()
            .values()
            .stream()
            .filter(partyInfo -> partyInfo.isLeaderOrMember(playerUuid))
            .findFirst()
            .orElse(null);
    }
}
