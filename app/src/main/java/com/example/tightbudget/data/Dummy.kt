import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Dummy(
    @PrimaryKey val id: Int = 1
)