'use client';

import { useRouter } from 'next/navigation';
import FruitBasket from '../../components/ui/icon/welcome/FruitBasket';
import FruitDrop2 from '../../components/ui/icon/welcome/FruitDrop2';
import Ellipse1 from '../../components/ui/icon/welcome/Ellipse1';
import Button from '../../components/ui/Button';
import { motion, Variants } from 'framer-motion';

// TODO: Fix the order of display (render the text section after rendering the basket)

export default function Welcome(): JSX.Element {
    const router = useRouter();
    const parentVariant: Variants = {
        initial: { opacity: 0 },
        animate: { opacity: 1, transition: { staggerChildren: 0.5 } }
    };
    const childVariant: Variants = {
        initial: { opacity: 0, scale: 0.5 },
        animate: { opacity: 1, scale: 1, transition: { duration: 0.5 } }
    };
    const textParentVariant: Variants = {
        initial: { opacity: 0 },
        animate: { opacity: 1, transition: { staggerChildren: 0.5 } }
    };
    const textChildVariant: Variants = {
        initial: { opacity: 0, y: 50 },
        animate: { opacity: 1, y: 0 }
    };
    return (
        <motion.div
            initial='initial'
            animate='animate'
            variants={parentVariant}
            className='flex flex-col h-screen justify-between'
        >
            <motion.div className='bg-primary flex flex-1 justify-center items-center'>
                <motion.div
                    initial='initial'
                    animate='animate'
                    variants={parentVariant}
                    className='flex flex-col items-end'
                >
                    <motion.div variants={childVariant} className='relative'>
                        <FruitDrop2 />
                    </motion.div>
                    <motion.div variants={childVariant}>
                        <div className='relative'>
                            <FruitBasket />
                        </div>
                        <div className='relative pt-2'>
                            <Ellipse1 />
                        </div>
                    </motion.div>
                </motion.div>
            </motion.div>
            <motion.div variants={textParentVariant} className='bg-white p-10 text-left'>
                <motion.div variants={textChildVariant} className=''>
                    <h2>Get The Freshest Fruit Salad Combo</h2>
                </motion.div>
                <motion.div variants={textChildVariant}>
                    <p className='text-gray-400'>
                        We deliver the best and freshest fruit salad in town. Order for a combo today!!!
                    </p>
                </motion.div>
                <motion.div variants={textChildVariant}>
                    <Button onClick={() => router.push('/login')}>Let’s Continue</Button>
                </motion.div>
            </motion.div>
        </motion.div>
    );
}
