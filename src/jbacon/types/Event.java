package jbacon.types;


/**
 * Created with IntelliJ IDEA.
 * User: freezerburn
 * Date: 1/23/13
 * Time: 1:42 PM
 */
public abstract class Event<T> {
    public static final String noMore = "veggies!";
    public static final String more = "moar bacon!";
    private static long nextUid = 0;

    protected T val;
    protected String error;
    protected final long timeOfCreation = System.nanoTime();
    private final long uid = nextUid++;

    protected Event() {
    }

    protected Event(Event<T> other) {
    }

    public static class ErrRet<T> {
        public T ret;
        public String eventStatus;
        public ErrRet(T ret, String eventStatus) {
            this.ret = ret;
            this.eventStatus = eventStatus;
        }
    }

    public static class Initial<T> extends Event<T> {
        public Initial(T val) {
            this.val = val;
        }

        @Override
        public T getValue() {
            return this.val;
        }

        @Override
        public String getError() {
            throw new UnsupportedOperationException("Cannot get an error from Event.Initial");
        }

        @Override
        public boolean hasValue() {
            return true;
        }

        @Override
        public boolean isInitial() {
            return true;
        }

        @Override
        public boolean isNext() {
            return false;
        }

        @Override
        public boolean isEnd() {
            return false;
        }

        @Override
        public boolean isError() {
            return false;
        }
    }

    public static class Next<T> extends Event<T> {
        public Next(T val) {
            this.val = val;
        }

        @Override
        public T getValue() {
            return this.val;
        }

        @Override
        public String getError() {
            throw new UnsupportedOperationException("Cannot get an error from Event.Next");
        }

        @Override
        public boolean hasValue() {
            return true;
        }

        @Override
        public boolean isInitial() {
            return false;
        }

        @Override
        public boolean isNext() {
            return true;
        }

        @Override
        public boolean isEnd() {
            return false;
        }

        @Override
        public boolean isError() {
            return false;
        }
    }

    public static class End<T> extends Event<T> {
        public End() {
        }

        @Override
        public T getValue() {
            throw new UnsupportedOperationException("Cannot get value from Event.End");
        }

        @Override
        public String getError() {
            throw new UnsupportedOperationException("Cannot get error from Event.End");
        }

        @Override
        public boolean hasValue() {
            return false;
        }

        @Override
        public boolean isInitial() {
            return false;
        }

        @Override
        public boolean isNext() {
            return false;
        }

        @Override
        public boolean isEnd() {
            return true;
        }

        @Override
        public boolean isError() {
            return false;
        }
    }

    public static class Error<T> extends Event<T> {
        public Error(String err) {
            this.error = err;
        }

        @Override
        public T getValue() {
            throw new UnsupportedOperationException("Cannot get value from Event.Error");
        }

        @Override
        public String getError() {
            return this.error;
        }

        @Override
        public boolean hasValue() {
            return false;
        }

        @Override
        public boolean isInitial() {
            return false;
        }

        @Override
        public boolean isNext() {
            return false;
        }

        @Override
        public boolean isEnd() {
            return false;
        }

        @Override
        public boolean isError() {
            return true;
        }
    }

    public abstract T getValue();
    public abstract String getError();
    public abstract boolean hasValue();

    public abstract boolean isInitial();
    public abstract boolean isNext();
    public abstract boolean isEnd();
    public abstract boolean isError();

    public long getUid() {
        return this.uid;
    }

    public long getTime() {
        return this.timeOfCreation;
    }

    public long getTimeInMillis() {
        return this.timeOfCreation / 10000000;
    }

    public float getTimeInSec() {
        return this.timeOfCreation / 1000000000.0f;
    }
}
