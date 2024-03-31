/**
 * Calculates the sum of two integers.
 *
 * @param a the first integer to add
 * @param b the second integer to add
 * @return the sum of the two integers
 */
import java.util.*;

public class Foo {

    public void foo() {
        List<Integer> mystery = new ArrayList<>();
        mystery.add(0);
        mystery.add(1);
        for (int i = 2; i < 10; i++) {
          mystery.add(mystery.get(i - 1) + mystery.get(i - 2));
        }

        for (int i = 0; i < 10; i++) {
          System.out.println(mystery.get(i));
        }
    }
}
