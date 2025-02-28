package codemining.util;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class ObjectCloner {
    // so that nobody can accidentally create an ObjectCloner object
    private ObjectCloner() {
    }

    // returns a deep copy of an object
    static public synchronized <E> E deepCopy(E oldObj) throws Exception {
        ObjectOutputStream oos = null;
        ObjectInputStream ois = null;
        try {
            ByteArrayOutputStream bos =
                    new ByteArrayOutputStream(); // A
            oos = new ObjectOutputStream(bos); // B
            // serialize and pass the object
            oos.writeObject(oldObj);   // C
            oos.flush();               // D
            ByteArrayInputStream bin =
                    new ByteArrayInputStream(bos.toByteArray()); // E
            ois = new ObjectInputStream(bin);                  // F
            // return the new object
            return (E) ois.readObject(); // G
        } catch (Exception e) {
            System.out.println("Exception in ObjectCloner = ");
            throw (e);
        } finally {
            if (oos != null)
                oos.close();

            if (ois != null)
                ois.close();
        }
    }

}
