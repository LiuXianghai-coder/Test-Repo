package org.xhliu;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class Application {
    public static void main(String[] args) {
        try (
                BufferedReader reader = new BufferedReader(new FileReader("D:/data/sort.txt"));
        ) {
            String line;

            BigDecimal divide = BigDecimal.valueOf(1_000_000L);
            while ((line = reader.readLine()) != null) {
                String[] strings = line.split("\t");
                int x = Integer.parseInt(strings[0]);
                double b = Double.parseDouble(strings[1]);
                double p = Double.parseDouble(strings[2]);

                double b1 = BigDecimal
                        .valueOf(b)
                        .setScale(2, RoundingMode.HALF_UP)
                        .divide(divide, RoundingMode.HALF_UP)
                        .doubleValue();
                double p1 = BigDecimal
                        .valueOf(p)
                        .setScale(2, RoundingMode.HALF_UP)
                        .divide(divide, RoundingMode.HALF_UP)
                        .doubleValue();

                System.out.println(x + "," + b1 + "," + p1);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
