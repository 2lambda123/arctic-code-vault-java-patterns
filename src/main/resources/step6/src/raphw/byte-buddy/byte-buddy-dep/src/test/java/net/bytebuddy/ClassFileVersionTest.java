package net.bytebuddy; // (rank 682) copied from https://github.com/raphw/byte-buddy/blob/8221f53b53e9148cd1b005085e298730c9c4f31e/byte-buddy-dep/src/test/java/net/bytebuddy/ClassFileVersionTest.java

import java.lang.reflect.Field;
import java.util.regex.Pattern;

import org.junit.Test;
import org.objectweb.asm.Opcodes;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

public class ClassFileVersionTest {

    @Test
    public void testExplicitConstructionOfUnknownVersion() throws Exception {
        double version = 0d;
        int value = 0;
        Pattern pattern = Pattern.compile("V[0-9]+(_[0-9]+)?");
        for (Field field : Opcodes.class.getFields()) {
            if (pattern.matcher(field.getName()).matches()) {
                if (version < Double.parseDouble(field.getName().substring(1).replace('_', '.'))) {
                    value = field.getInt(null);
                }
            }
        }
        assertThat(ClassFileVersion.ofMinorMajor(value + 1).getMinorMajorVersion(), is(value + 1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIllegalVersion() throws Exception {
        ClassFileVersion.ofMinorMajor(ClassFileVersion.BASE_VERSION);
    }

    @Test
    public void testComparison() throws Exception {
        assertThat(new ClassFileVersion(Opcodes.V1_1).compareTo(new ClassFileVersion(Opcodes.V1_1)), is(0));
        assertThat(new ClassFileVersion(Opcodes.V1_1).compareTo(new ClassFileVersion(Opcodes.V1_2)), is(-1));
        assertThat(new ClassFileVersion(Opcodes.V1_2).compareTo(new ClassFileVersion(Opcodes.V1_1)), is(1));
        assertThat(new ClassFileVersion(Opcodes.V1_2).compareTo(new ClassFileVersion(Opcodes.V1_2)), is(0));
        assertThat(new ClassFileVersion(Opcodes.V1_3).compareTo(new ClassFileVersion(Opcodes.V1_2)), is(1));
        assertThat(new ClassFileVersion(Opcodes.V1_2).compareTo(new ClassFileVersion(Opcodes.V1_3)), is(-1));
    }

    @Test
    public void testVersionPropertyAction() throws Exception {
        assertThat(ClassFileVersion.VersionLocator.ForLegacyVm.INSTANCE.run(), is(System.getProperty("java.version")));
    }

    @Test
    public void testVersionOfClass() throws Exception {
        assertThat(ClassFileVersion.of(Foo.class).compareTo(ClassFileVersion.ofThisVm()) < 1, is(true));
    }

    @Test
    public void testClassFile() throws Exception {
        assertThat(ClassFileVersion.of(Object.class).getMinorMajorVersion(), not(0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIllegalClassFile() throws Exception {
        ClassFileVersion.ofClassFile(new byte[0]);
    }

    private static class Foo {
        /* empty */
    }
}
