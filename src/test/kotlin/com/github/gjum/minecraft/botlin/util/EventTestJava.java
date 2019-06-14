package com.github.gjum.minecraft.botlin.util;

import com.github.gjum.minecraft.botlin.api.AvatarEvents;
import com.github.gjum.minecraft.botlin.state.Entity;
import org.junit.Test;

import java.util.ArrayList;

import static com.github.gjum.minecraft.botlin.util.JavaCallbackHelper.$;
import static org.junit.Assert.assertEquals;

public class EventTestJava {
    @Test
    public void eventTest() {
        final EventEmitterImpl<AvatarEvents> ee = new EventEmitterImpl<>();

        final ArrayList<String> called = new ArrayList<>();

        // single arg
        ee.on(AvatarEvents.getSpawned(), $(entity -> {
            called.add("AAA Spawned " + entity.getPosition());
        }));
        // three args
        ee.on(AvatarEvents.getPositionChanged(), $((newPos, oldPos, reason) -> {
            called.add("PosChange " + newPos);
        }));

        ee.emit(AvatarEvents.getPositionChanged(), $(it -> {
            it.invoke(new Vec3d(2.0, 2.0, 2.0), Vec3d.getOrigin(), null);
        }));
        ee.emit(AvatarEvents.getSpawned(), it -> {
            final Entity e = new Entity(42);
            e.setPosition(new Vec3d(3.0, 3.0, 3.0));
            return it.invoke(e);
        });

        assertEquals("PosChange [2.0 2.0 2.0]", called.get(0));
        assertEquals("AAA Spawned [3.0 3.0 3.0]", called.get(1));
        assertEquals(2, called.size());
    }
}
